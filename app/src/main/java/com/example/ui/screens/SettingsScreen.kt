@file:Suppress("FunctionName")

package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.example.ui.util.findActivity
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.OnlineStatus
import com.example.data.model.RelationshipStatus
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.UpsellBorderPink
import com.example.ui.theme.VipGold
import com.example.ui.theme.VipGoldBorder
import com.example.ui.theme.VipGoldContainer
import com.example.ui.theme.VipGoldText
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel

import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString

@Composable
fun SettingsScreen(viewModel: InLoveViewModel) {
  val notificationEnabled by viewModel.notificationsEnabled.collectAsState()
  val soundEnabled by viewModel.soundEnabled.collectAsState()

  val boyName by viewModel.boyName.collectAsState()
  val girlName by viewModel.girlName.collectAsState()
  val boyAvatarUrl by viewModel.boyAvatarUrl.collectAsState()
  val girlAvatarUrl by viewModel.girlAvatarUrl.collectAsState()
  val boyBirthDate by viewModel.boyBirthDate.collectAsState()
  val boyAge by viewModel.boyAge.collectAsState()
  val boyZodiac by viewModel.boyZodiac.collectAsState()
  val girlBirthDate by viewModel.girlBirthDate.collectAsState()
  val girlAge by viewModel.girlAge.collectAsState()
  val girlZodiac by viewModel.girlZodiac.collectAsState()
  val loveDays by viewModel.loveDays.collectAsState()
  val loveTitle by viewModel.loveTitle.collectAsState()
  var showEditLoveTitleDialog by remember { mutableStateOf(false) }

  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == AppLanguage.EN
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val clipboardManager = LocalClipboardManager.current

  // Online 1-1 Set Love States
  val currentUser by viewModel.currentOnlineUser.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val activeRelationship by viewModel.activeRelationship.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val incomingInvite by viewModel.incomingInvite.collectAsState()
  val isVip by viewModel.isVip.collectAsState()
  var showConfirmBreakupDialog by remember { mutableStateOf(false) }

  // Account & Security States
  val authState by viewModel.authState.collectAsState()
  var showChangePasswordDialog by remember { mutableStateOf(false) }
  val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
  var facebookLinked by remember { mutableStateOf(viewModel.authRepo.isFacebookLinked) }
  var showSetPinDialog by remember { mutableStateOf(false) }
  var showSecurityAuditLogsDialog by remember { mutableStateOf(false) }
  var showDevicesDialog by remember { mutableStateOf(false) }
  var showPrivacyVaultInfoDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }

  val notifEnabledToast = stringResource(R.string.settings_notif_enabled_toast)
  val notifDisabledToast = stringResource(R.string.settings_notif_disabled_toast)
  val dbSyncToast = stringResource(R.string.settings_db_sync_toast)
  val aboutToast = stringResource(R.string.settings_about_toast)

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      val isOnlineNow by viewModel.isOnline.collectAsState()
      val mode = com.example.domain.AppMode.resolve(
        signedIn = authState is com.example.data.repository.AuthState.Authenticated,
        paired = relationshipStatus == com.example.data.model.OnlineStatus.COUPLED
      )
      Text(
        text = com.example.domain.AppMode.describe(mode, isOnlineNow, isEnglish),
        fontSize = 12.sp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // 1. User's Own Profile Card (Unified Single Card with Search/Couple Code & Copy)
    item {
      Text(
        text = if (isEnglish) "YOUR PROFILE" else "HỒ SƠ CỦA BẠN",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(6.dp))

      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color.White.copy(alpha = 0.95f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFB6C1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { viewModel.openEditProfileDialog() }
          .testTag("settings_my_profile_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Box(modifier = Modifier.size(56.dp)) {
              AsyncImage(
                model = currentUser.avatarUrl.ifEmpty { boyAvatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" } },
                contentDescription = if (isEnglish) "Your Avatar" else "Ảnh của bạn",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .border(2.dp, Primary, CircleShape)
              )
              Surface(
                shape = CircleShape,
                color = Primary,
                modifier = Modifier
                  .size(18.dp)
                  .align(Alignment.BottomEnd)
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.padding(3.dp)
                )
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = currentUser.effectiveDisplayName.ifEmpty { boyName.ifEmpty { if (isEnglish) "You" else "Bạn" } },
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnSurface
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Primary.copy(alpha = 0.12f)
                ) {
                  Text(
                    text = if (isEnglish) "YOU" else "BẠN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(2.dp))
              val boySub = listOfNotNull(
                if (currentUser.age > 0) "${currentUser.age}t" else if (boyAge > 0) "${boyAge}t" else null,
                currentUser.zodiac.ifBlank { boyZodiac.ifBlank { null } },
                boyBirthDate.ifBlank { null }
              ).joinToString(" • ")
              Text(
                text = boySub.ifEmpty { if (isEnglish) "Tap to edit personal info" else "Chạm để chỉnh sửa thông tin cá nhân" },
                fontSize = 12.sp,
                color = OnSurfaceVariant
              )
            }

            IconButton(
              onClick = { viewModel.openEditProfileDialog() },
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = if (isEnglish) "Edit personal profile" else "Chỉnh sửa hồ sơ cá nhân",
                tint = Primary
              )
            }
          }

          // Search / Couple Code Row with 1-Tap Copy
          val coupleCode = currentUser.coupleCode.ifBlank { "INLOVE-${currentUser.uid.take(6).uppercase()}" }
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF0F5),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF80AB).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isEnglish) "Your Pairing / Search Code:" else "Mã tìm kiếm ghép đôi của bạn:",
                  fontSize = 11.sp,
                  color = Color(0xFF880E4F),
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = coupleCode,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFFC2185B),
                  letterSpacing = 1.sp
                )
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE91E63),
                modifier = Modifier.clickable {
                  clipboardManager.setText(AnnotatedString(coupleCode))
                  viewModel.showToast(if (isEnglish) "Copied code: $coupleCode" else "Đã sao chép mã: $coupleCode")
                }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                  )
                  Text(
                    text = if (isEnglish) "Copy" else "Sao chép",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }
          }

          if (!currentUser.isProfileSetup) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFFF3E0),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = if (isEnglish) "👉 Tap here to update your name, birthday & zodiac!" else "👉 Bạn chưa cập nhật hồ sơ. Hãy bấm vào đây để nhập tên & ngày sinh!",
                fontSize = 11.sp,
                color = Color(0xFFE65100),
                modifier = Modifier.padding(8.dp)
              )
            }
          }
        }
      }
    }

    // 2. Partner & Relationship Status (Unified Offline & Online 1-1)
    item {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = if (isEnglish) "PARTNER & LOVE STATUS (SET LOVE 1-1)" else "NGƯỜI THƯƠNG & TRẠNG THÁI TÌNH CẢM (SET LOVE 1-1)",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFC2185B)
      )
      Spacer(modifier = Modifier.height(6.dp))

      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("partner_relationship_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          val isCoupled = relationshipStatus == OnlineStatus.COUPLED && partnerUser != null

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            AsyncImage(
              model = if (isCoupled) partnerUser?.avatarUrl.orEmpty().ifEmpty { girlAvatarUrl } else girlAvatarUrl.ifEmpty { "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200" },
              contentDescription = "Partner Avatar",
              modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFFF4081), CircleShape),
              contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (isCoupled) partnerUser?.displayName.orEmpty() else girlName.ifEmpty { if (isEnglish) "Partner" else "Người thương" },
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (isCoupled) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                  Text(
                    text = if (isCoupled) "COUPLED" else (if (isEnglish) "OFFLINE" else "CHẾ ĐỘ MÁY"),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCoupled) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                  )
                }
              }

              val partnerSubtitle = if (isCoupled) {
                val loveSince = activeRelationship?.startDateText?.ifBlank { null }
                if (!loveSince.isNullOrBlank()) (if (isEnglish) "Code: ${partnerUser?.coupleCode.orEmpty()} • In love since $loveSince" else "Mã: ${partnerUser?.coupleCode.orEmpty()} • Yêu từ $loveSince")
                else (if (isEnglish) "Code: ${partnerUser?.coupleCode.orEmpty()}" else "Mã: ${partnerUser?.coupleCode.orEmpty()}")
              } else {
                val girlSub = listOfNotNull(
                  if (girlAge > 0) "${girlAge}t" else null,
                  girlZodiac.ifBlank { null },
                  girlBirthDate.ifBlank { null }
                ).joinToString(" • ")
                girlSub.ifEmpty { if (isEnglish) "Offline partner configured on device" else "Người thương thiết lập trên máy" }
              }
              Text(
                text = partnerSubtitle,
                fontSize = 12.sp,
                color = Color.Gray
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Two-way Breakup Alert
          val isPendingBreakup = activeRelationship?.status == RelationshipStatus.PENDING_BREAKUP
          val requestedByPartner = isPendingBreakup && activeRelationship?.breakupRequestedBy != currentUser.uid
          val requestedByMe = isPendingBreakup && activeRelationship?.breakupRequestedBy == currentUser.uid

          if (requestedByPartner) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE53935)),
              modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isEnglish) "Partner requested to end Set Love!" else "Đối phương đã gửi yêu cầu hủy Set Love!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFB71C1C)
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = if (isEnglish) "Do you agree to cancel pairing and return to Single status?" else "Bạn có đồng ý hủy ghép đôi và trở về trạng thái Độc thân?",
                  fontSize = 12.sp,
                  color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutlinedButton(
                    onClick = { viewModel.rejectBreakup() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("reject_breakup_button")
                  ) {
                    Text(if (isEnglish) "Reject" else "Từ chối", fontSize = 12.sp)
                  }
                  Button(
                    onClick = { viewModel.confirmBreakup() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("confirm_breakup_button")
                  ) {
                    Text(if (isEnglish) "Confirm" else "Xác nhận", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          } else if (requestedByMe) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA000)),
              modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = if (isEnglish) "⏳ Waiting for partner to confirm breakup..." else "⏳ Đang chờ đối phương xác nhận yêu cầu hủy Set Love...",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = Color(0xFFE65100)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { viewModel.rejectBreakup() },
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Text(if (isEnglish) "Withdraw" else "Rút lại yêu cầu", fontSize = 12.sp)
                  }
                  Button(
                    onClick = { viewModel.forceBreakup() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Text(if (isEnglish) "Force Cancel" else "Cưỡng chế hủy ngay", fontSize = 12.sp)
                  }
                }
              }
            }
          }

          // Incoming Invite Banner in Settings
          if (incomingInvite != null) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4081)),
              modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE91E63),
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (isEnglish) "Couple invitation from ${incomingInvite?.effectiveSenderName.orEmpty()}" else "Lời mời ghép đôi từ ${incomingInvite?.effectiveSenderName.orEmpty()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF880E4F)
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = (if (isEnglish) "Proposed start date: " else "Đề xuất ngày bắt đầu: ") + (incomingInvite?.proposedStartDateText.orEmpty().ifEmpty { if (isEnglish) "Not set" else "Chưa đặt" }),
                  fontSize = 12.sp,
                  color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                  onClick = { viewModel.openPairingScreen() },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(if (isEnglish) "Inspect Identity & Confirm" else "Kiểm tra danh tính & Xác nhận", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Action Buttons:
          // In Offline mode: "Chỉnh sửa người thương" & "Ghép đôi Online 1-1"
          // In Online coupled mode: "Trang Ghép Đôi" & "Hủy Set Love"
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (isCoupled) {
              OutlinedButton(
                onClick = { viewModel.openPairingScreen() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("open_pairing_screen_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Link,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isEnglish) "Pairing Page" else "Trang Ghép Đôi", fontSize = 12.sp)
              }

              if (!isPendingBreakup) {
                Button(
                  onClick = { showConfirmBreakupDialog = true },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828)
                  ),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).testTag("request_breakup_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.HeartBroken,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(if (isEnglish) "Cancel Set Love" else "Hủy Set Love", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            } else {
              OutlinedButton(
                onClick = { viewModel.openEditCoupleDialog() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("edit_offline_partner_button")
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = Primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isEnglish) "Edit Partner" else "Chỉnh Người Thương", fontSize = 12.sp)
              }

              Button(
                onClick = { viewModel.openPairingScreen() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("go_online_pairing_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Link,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isEnglish) "Pair Online 1-1" else "Ghép Đôi 1-1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    // 2. Language Selection Toggle (Primary Feature Highlight)
    item {
      LanguageSelectionToggleCard(
        currentLanguage = appLanguage,
        onLanguageChanged = { newLang -> viewModel.setLanguage(newLang) },
        onOpenDialog = { viewModel.openLanguageDialog() }
      )
    }

    // 2.1. Ad privacy options (GDPR/CCPA "manage consent" re-entry point)
    item {
      val context = androidx.compose.ui.platform.LocalContext.current
      // NOT `context as? Activity` — MainActivity wraps LocalContext with
      // createConfigurationContext() for locale support, so it is never an Activity here.
      // LocalActivityResultRegistryOwner is provided once, directly around the real
      // Activity, in the same MainActivity.onCreate setContent block, specifically usable
      // for this.
      val activity = androidx.activity.compose.LocalActivityResultRegistryOwner.current as? android.app.Activity
      val showPrivacyRow by com.example.privacy.AppPrivacyCoordinator.privacyOptionsRequired.collectAsState()
      AdPrivacyOptionsRow(
        visible = showPrivacyRow && activity != null,
        isEnglish = isEnglish,
        onClick = {
          val act = activity ?: return@AdPrivacyOptionsRow
          com.example.privacy.AppPrivacyCoordinator.showPrivacyOptions(act)
        }
      )
      Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { uriHandler.openUri(com.example.config.LegalLinks.privacyPolicy) }) {
          Text(stringResource(R.string.settings_legal_privacy), fontSize = 12.sp)
        }
        TextButton(onClick = { uriHandler.openUri(com.example.config.LegalLinks.termsOfService) }) {
          Text(stringResource(R.string.settings_legal_terms), fontSize = 12.sp)
        }
      }
    }

    // 2.5. VIP Subscription Banner & Entry Point to Paywall
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isVip) VipGoldContainer else SurfaceContainerLow
          ),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isVip) VipGoldBorder else UpsellBorderPink
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setVipDialogVisible(true) }
            .testTag("settings_vip_upgrade_card")
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isVip) VipGold else Primary),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isVip) (if (isEnglish) "InLove VIP Member 👑" else "Thành Viên InLove VIP 👑") else (if (isEnglish) "Upgrade to Love VIP ✨" else "Nâng Cấp Gói VIP Tình Yêu ✨"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isVip) VipGoldText else Primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                // Chỉ liệt kê quyền lợi ĐÃ XÁC MINH thật sự dành riêng cho VIP: tắt quảng cáo
                // là quyền lợi duy nhất được BillingManager/AdsManagerImpl gate theo isVip.
                // "Cloud sync" (Firebase3NFService) và "AI không giới hạn" (gợi ý quà) đã bị
                // xoá khỏi dòng này vì cả hai đều KHÔNG được gate theo VIP trong code thực tế
                // (sync cloud là dead code chưa từng gọi; gợi ý quà mở cho mọi người dùng miễn
                // phí) — Google Play Policy cấm quảng cáo sai sự thật về tính năng.
                text = if (isVip) (if (isEnglish) "Completely ad-free • Smooth, uninterrupted experience" else "Tắt quảng cáo hoàn toàn • Trải nghiệm mượt mà không gián đoạn")
                       else (if (isEnglish) "Ad-free experience • 3-day free trial on Annual plan" else "Tắt sạch quảng cáo • Thử miễn phí 3 ngày gói Năm"),
                fontSize = 12.sp,
                color = OnSurfaceVariant
              )
            }
            Icon(
              imageVector = Icons.Filled.ChevronRight,
              contentDescription = null,
              tint = if (isVip) VipGold else Primary
            )
          }
        }

        // Nút Quản Lý & Hủy Gói Thuê Bao (Google Play Policy Bắt Buộc)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=com.aistudio.inlove.kmrv")
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Quản lý gói Google Play",
            tint = Color(0xFF8A2E5B),
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isEnglish) "Manage & Cancel Google Play Subscriptions" else "Quản lý & Hủy gói cước trên Google Play",
            fontSize = 12.5.sp,
            color = Color(0xFF8A2E5B),
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(
              textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
          )
        }
      }
    }

    // 3. Settings Group: Quản Lý Kỷ Niệm & Dữ Liệu
    item {
      Text(
        text = stringResource(R.string.settings_section_profile),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SettingClickableRow(
            icon = Icons.Filled.CameraAlt,
            title = stringResource(R.string.settings_capture_memory_title),
            subtitle = stringResource(R.string.settings_capture_memory_sub),
            onClick = { viewModel.openMemoryDialog() }
          )

          SettingClickableRow(
            icon = Icons.Filled.CloudSync,
            title = stringResource(R.string.settings_db_sync_title),
            subtitle = stringResource(R.string.settings_db_sync_sub),
            onClick = { viewModel.showToast(dbSyncToast) }
          )
        }
      }
    }

    // 4. Settings Group: Giao Diện & Hình Nền
    item {
      Text(
        text = stringResource(R.string.settings_section_appearance),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SettingClickableRow(
            icon = Icons.Filled.Favorite,
            title = if (isEnglish) "Customize Love Journey Title" else "Đổi Tiêu Đề Hành Trình Yêu",
            subtitle = loveTitle,
            onClick = { showEditLoveTitleDialog = true }
          )

          SettingClickableRow(
            icon = Icons.Filled.Wallpaper,
            title = stringResource(R.string.settings_wallpaper_title),
            subtitle = stringResource(R.string.settings_wallpaper_sub),
            onClick = { viewModel.openWallpaperDialog() }
          )

          SettingClickableRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.settings_guide_title),
            subtitle = stringResource(R.string.settings_guide_sub),
            onClick = { viewModel.openGuideDialog() }
          )
        }
      }
    }

    // 5. Settings Group: Thông Báo & Lời Nhắc
    item {
      Text(
        text = stringResource(R.string.settings_section_notifications),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SettingSwitchRow(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.settings_notif_anniversary_title),
            subtitle = stringResource(R.string.settings_notif_anniversary_sub),
            checked = notificationEnabled,
            onCheckedChange = {
              viewModel.setNotificationsEnabled(it)
              viewModel.showToast(if (it) notifEnabledToast else notifDisabledToast)
            }
          )

          SettingSwitchRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = stringResource(R.string.settings_notif_sound_title),
            subtitle = stringResource(R.string.settings_notif_sound_sub),
            checked = soundEnabled,
            onCheckedChange = { viewModel.setSoundEnabled(it) }
          )
        }
      }
    }

    // 6. Settings Group: Dữ Liệu & Bảo Mật Nâng Cao (Security & Privacy Center)
    item {
      Text(
        text = if (isEnglish) "ACCOUNT & ADVANCED SECURITY" else "TÀI KHOẢN & BẢO MẬT NÂNG CAO",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(8.dp))

      val currentAccount = (authState as? com.example.data.repository.AuthState.Authenticated)?.account
      val isPinActive = currentAccount?.isPinEnabled == true

      // Dynamic Security Health Score Calculation (0 - 100). No biometric term: this app has no
      // biometric auth integration at all (Fingerprint/Face) — the toggle that used to add 15
      // fake points here did nothing real, same false-claim class already removed from the VIP
      // paywall copy elsewhere. Rebalanced so the 2 remaining real factors still reach 100.
      val securityScore = remember(currentAccount, isPinActive) {
        var score = 40 // Base encryption score
        if (currentAccount != null) score += 30
        if (isPinActive) score += 30
        score
      }

      val (scoreColor, scoreGrade) = when {
        securityScore >= 90 -> Color(0xFF2E7D32) to if (isEnglish) "Excellent" else "Rất an toàn"
        securityScore >= 70 -> Color(0xFF1976D2) to if (isEnglish) "Good" else "Khá an toàn"
        securityScore >= 50 -> Color(0xFFF57C00) to if (isEnglish) "Fair" else "Mức trung bình"
        else -> Color(0xFFD32F2F) to if (isEnglish) "Needs Attention" else "Cần nâng cấp"
      }

      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFD1DC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          // --- Security Health Assessment Header Card ---
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = scoreColor.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = scoreColor,
                    modifier = Modifier.size(24.dp)
                  )
                }
                Column {
                  Text(
                    text = if (isEnglish) "Security Health: $scoreGrade" else "Đánh giá an toàn: $scoreGrade",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = scoreColor
                  )
                  Text(
                    text = if (isEnglish) "Score: $securityScore/100 • End-to-End Encrypted" else "Điểm: $securityScore/100 • Mã hóa đầu-cuối",
                    fontSize = 11.5.sp,
                    color = Color(0xFF616161)
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(10.dp),
                color = scoreColor
              ) {
                Text(
                  text = "$securityScore%",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // --- Account Profile Header / Guest Mode Status ---
          if (currentAccount != null) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFFCE4EC)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = null,
                  tint = Color(0xFFE91E63),
                  modifier = Modifier.size(24.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = currentAccount.displayName,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = Color(0xFF212121)
                )
                Text(
                  text = currentAccount.email,
                  fontSize = 12.sp,
                  color = Color(0xFF757575)
                )
              }
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFE8F5E9)
              ) {
                Text(
                  text = if (isEnglish) "Verified" else "Đã xác thực",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF2E7D32),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            androidx.compose.material3.HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(10.dp))
          } else {
            // Guest / Offline Mode Notice & Call-to-action
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFFFFF0F5),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFF80AB)),
              modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color(0xFFC2185B),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isEnglish) "Guest Mode (Offline)" else "Chế độ Khách (Offline)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF880E4F)
                  )
                }
                Text(
                  text = if (isEnglish)
                    "Your love days and memories are stored safely and privately on this local device."
                  else
                    "Dữ liệu đếm ngày yêu và kỷ niệm của bạn đang được lưu trữ an toàn riêng tư trực tiếp trên máy.",
                  fontSize = 12.sp,
                  lineHeight = 17.sp,
                  color = Color(0xFF424242)
                )
                Button(
                  onClick = { viewModel.openAuthScreen() },
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isEnglish) "Sign In / Register for Cloud Sync" else "Đăng Nhập / Đăng Ký để Đồng Bộ Đám Mây",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                  )
                }
              }
            }
          }

          // --- Protection Controls: PIN, Biometrics, Vault ---
          // 1. PIN Protection Switch
          SettingSwitchRow(
            icon = Icons.Filled.Lock,
            title = if (isEnglish) "App Lock with 4-Digit PIN" else "Khóa ứng dụng bằng PIN",
            subtitle = if (isPinActive) {
              if (isEnglish) "4-digit passcode protection active" else "Đang bật mã PIN 4 số bảo vệ"
            } else {
              if (isEnglish) "Passcode protection disabled" else "Chưa bật bảo vệ PIN"
            },
            checked = isPinActive,
            onCheckedChange = { enabled ->
              if (enabled && (currentAccount?.appPin.isNullOrEmpty())) {
                showSetPinDialog = true
              } else {
                viewModel.togglePinEnabled(enabled)
              }
            }
          )

          // 2. Change PIN Button
          SettingClickableRow(
            icon = Icons.Filled.Key,
            title = if (isEnglish) "Setup / Change PIN Passcode" else "Cài đặt / Đổi mã PIN",
            subtitle = if (isEnglish) "Configure 4-digit quick unlock passcode" else "Thiết lập mã 4 số riêng tư mở khóa nhanh",
            onClick = { showSetPinDialog = true }
          )

          // Biometric Authentication toggle removed: this app has no biometric (fingerprint/
          // face) integration anywhere — it only ever simulated turning one on, same false
          // claim already stripped from the VIP paywall copy elsewhere in this app. Re-add a
          // real toggle here only alongside an actual BiometricPrompt integration.

          // 3. Privacy Vault Information
          SettingClickableRow(
            icon = Icons.Filled.VisibilityOff,
            title = if (isEnglish) "Privacy Vault & App Concealment" else "Két an toàn & Chế độ riêng tư",
            subtitle = if (isEnglish) "Conceal sensitive dates, notes, and photos" else "Bảo vệ ẩn dữ liệu nhật ký và kỷ niệm nhạy cảm",
            onClick = { showPrivacyVaultInfoDialog = true }
          )

          // 5. Active Sessions & Connected Devices
          SettingClickableRow(
            icon = Icons.Filled.Devices,
            title = if (isEnglish) "Active Devices & Sessions" else "Thiết bị & Phiên hoạt động",
            subtitle = if (isEnglish) "Manage authorized devices accessing this account" else "Quản lý các thiết bị đang đăng nhập tài khoản",
            onClick = { showDevicesDialog = true }
          )

          // Account-specific options (Change Password & Audit Logs)
          if (currentAccount != null) {
            // Change Password Button (provider-only accounts have no local password)
            if (currentAccount?.let { !viewModel.authRepo.isProviderOnly(it) } != false) {
              SettingClickableRow(
                icon = Icons.Filled.Shield,
                title = if (isEnglish) "Change Account Password" else "Đổi mật khẩu tài khoản",
                subtitle = if (isEnglish) "Require current password & strength check" else "Yêu cầu mật khẩu cũ & đánh giá độ mạnh",
                onClick = { showChangePasswordDialog = true }
              )
            }

            // Facebook link: lets the user sign in / unlock / recover a forgotten PIN with Facebook
            SettingClickableRow(
              icon = Icons.Filled.Key,
              title = if (isEnglish) "Facebook sign-in & unlock" else "Đăng nhập & mở khóa bằng Facebook",
              subtitle = if (facebookLinked) {
                if (isEnglish) "Linked — can unlock the app and recover a forgotten PIN" else "Đã liên kết — mở khóa và khôi phục khi quên PIN"
              } else {
                if (isEnglish) "Tap to link your Facebook account" else "Chạm để liên kết tài khoản Facebook"
              },
              onClick = {
                if (!facebookLinked) {
                  val activity = context.findActivity()
                  if (activity != null) settingsScope.launch {
                    val result = viewModel.authRepo.linkFacebook(activity)
                    viewModel.showToast(result.second)
                    facebookLinked = viewModel.authRepo.isFacebookLinked
                  }
                }
              }
            )

            // Security Audit Logs
            SettingClickableRow(
              icon = Icons.Filled.History,
              title = if (isEnglish) "Security Audit Logs" else "Nhật ký bảo mật",
              subtitle = if (isEnglish) "Review sign-in history and lockout alerts" else "Xem lịch sử đăng nhập, cảnh báo thử sai và đổi mật khẩu",
              onClick = { showSecurityAuditLogsDialog = true }
            )
          }

          // Lock App Now (if PIN enabled)
          if (isPinActive) {
            SettingClickableRow(
              icon = Icons.Filled.LockOpen,
              title = if (isEnglish) "Lock Application Now" else "Khóa ứng dụng ngay",
              subtitle = if (isEnglish) "Immediately require PIN when resuming app" else "Yêu cầu nhập mã PIN khi dùng tiếp",
              onClick = { viewModel.lockApp() }
            )
          }

          // Sync Enriched Cloud Presets from Firestore
          SettingClickableRow(
            icon = Icons.Filled.CloudSync,
            title = if (isEnglish) "Sync Cloud Firestore Data" else "Đồng bộ dữ liệu Cloud Firestore",
            subtitle = if (isEnglish) "Refresh gift ideas, badges, and milestones" else "Làm mới gợi ý quà tặng, huy hiệu và mốc kỷ niệm",
            onClick = { viewModel.syncCloudData() }
          )

          SettingClickableRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_sub),
            onClick = { viewModel.showToast(aboutToast) }
          )

          if (currentAccount != null) {
            Spacer(modifier = Modifier.height(14.dp))

            // Logout Button
            OutlinedButton(
              onClick = { showLogoutConfirmDialog = true },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_logout")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isEnglish) "Sign Out (Switch to Guest Mode)" else "Đăng Xuất Tài Khoản (Về Chế Độ Khách)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
              onClick = { showDeleteAccountConfirmDialog = true },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_delete_account")
            ) {
              Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isEnglish) "Permanently Delete Account & Data" else "Xóa Vĩnh Viễn Tài Khoản & Dữ Liệu",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }
      }
    }
  }

  // Confirmation Dialog for Requesting Breakup
  if (showConfirmBreakupDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmBreakupDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.HeartBroken,
          contentDescription = null,
          tint = Color(0xFFD32F2F),
          modifier = Modifier.size(36.dp)
        )
      },
      title = {
        Text(text = if (isEnglish) "Send request to cancel Set Love?" else "Gửi yêu cầu hủy Set Love?", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(if (isEnglish) "The system will notify ${partnerUser?.displayName ?: "partner"} to confirm ending Set Love. Once confirmed, your status will return to Single." else "Hệ thống sẽ gửi thông báo hủy ghép đôi đến ${partnerUser?.displayName ?: "đối phương"}. Khi đối phương đồng ý (hoặc sau thời gian chờ), trạng thái sẽ chuyển về Độc thân và tạm khóa kỷ niệm chung.")
      },
      confirmButton = {
        Button(
          onClick = {
            showConfirmBreakupDialog = false
            viewModel.requestBreakup()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
          Text(if (isEnglish) "Send Request" else "Gửi yêu cầu")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showConfirmBreakupDialog = false }) {
          Text(if (isEnglish) "Cancel" else "Hủy bỏ")
        }
      }
    )
  }

  // Security Dialogs
  if (showChangePasswordDialog) {
    ChangePasswordDialog(
      viewModel = viewModel,
      onDismiss = { showChangePasswordDialog = false }
    )
  }

  if (showSetPinDialog) {
    SetPinDialog(
      viewModel = viewModel,
      onDismiss = { showSetPinDialog = false }
    )
  }

  if (showSecurityAuditLogsDialog) {
    SecurityAuditLogsDialog(
      viewModel = viewModel,
      onDismiss = { showSecurityAuditLogsDialog = false }
    )
  }

  // Active Devices Dialog
  if (showDevicesDialog) {
    AlertDialog(
      onDismissRequest = { showDevicesDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.Devices,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text(
          text = if (isEnglish) "Active Devices & Sessions" else "Thiết Bị & Phiên Đăng Nhập",
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF5F5F5),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Devices,
                  contentDescription = null,
                  tint = Color(0xFF2E7D32),
                  modifier = Modifier.size(20.dp)
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = android.os.Build.MODEL ?: "Android Device",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                  Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F5E9)) {
                    Text(
                      text = if (isEnglish) "THIS DEVICE" else "THIẾT BỊ NÀY",
                      fontSize = 8.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF2E7D32),
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                }
                Text(
                  text = if (isEnglish) "Android ${android.os.Build.VERSION.RELEASE} • Active now" else "Android ${android.os.Build.VERSION.RELEASE} • Đang hoạt động",
                  fontSize = 11.sp,
                  color = Color.Gray
                )
              }
            }
          }

          Text(
            text = if (isEnglish)
              "Your account is protected by hardware-backed token encryption. Only authorized devices can decrypt your shared love logs."
            else
              "Tài khoản của bạn được bảo vệ bằng mã hóa token trên thiết bị. Chỉ các thiết bị được cấp quyền mới có thể truy cập nhật ký tình yêu.",
            fontSize = 12.sp,
            color = Color(0xFF616161),
            lineHeight = 16.sp
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showDevicesDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
          Text(if (isEnglish) "Got It" else "Đã Hiểu")
        }
      }
    )
  }

  // Privacy Vault Info Dialog
  if (showPrivacyVaultInfoDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyVaultInfoDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.VisibilityOff,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text(
          text = if (isEnglish) "Privacy Vault & Concealment" else "Két An Toàn & Chế Độ Riêng Tư",
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF0F5),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = if (isEnglish) "🛡️ AES-256 Encrypted Local Storage" else "🛡️ Mã hóa dữ liệu lưu trữ AES-256",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = Color(0xFF880E4F)
              )
              Text(
                text = if (isEnglish)
                  "All memories, offline notes, and personal anniversary dates are encrypted in your private sandbox."
                else
                  "Tất cả kỷ niệm, ghi chú offline và ngày kỷ niệm đều được mã hóa an toàn trong bộ nhớ máy.",
                fontSize = 11.5.sp,
                color = Color(0xFF424242)
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF3E5F5),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = if (isEnglish) "🔒 Couple 1-1 Restricted Channel" else "🔒 Kênh truyền dữ liệu khép kín 1-1",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = Color(0xFF4A148C)
              )
              Text(
                text = if (isEnglish)
                  "Memories marked as 'Couple Only' can only be viewed by you and your paired partner. No third-party access allowed."
                else
                  "Kỷ niệm đặt chế độ 'Chỉ 2 người' chỉ duy nhất bạn và đối phương được xem. Không một ai khác có thể truy cập.",
                fontSize = 11.5.sp,
                color = Color(0xFF424242)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showPrivacyVaultInfoDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
          Text(if (isEnglish) "Close" else "Đóng")
        }
      }
    )
  }

  // Logout Confirmation Dialog
  if (showLogoutConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirmDialog = false },
      icon = {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ExitToApp,
          contentDescription = null,
          tint = Color(0xFFD32F2F),
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text(if (isEnglish) "Sign out of account?" else "Đăng xuất tài khoản?", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(if (isEnglish) "You will need to sign in again with your email and password to access synced data." else "Bạn sẽ cần đăng nhập lại bằng email và mật khẩu để tiếp tục sử dụng InLove.")
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirmDialog = false
            viewModel.logout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
          Text(if (isEnglish) "Sign Out" else "Đăng xuất")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showLogoutConfirmDialog = false }) {
          Text(if (isEnglish) "Cancel" else "Hủy")
        }
      }
    )
  }

  // Delete Account Confirmation Dialog (Google Play Policy Compliance)
  if (showDeleteAccountConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteAccountConfirmDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.DeleteForever,
          contentDescription = null,
          tint = Color(0xFFC62828),
          modifier = Modifier.size(36.dp)
        )
      },
      title = {
        Text(if (isEnglish) "Delete account & data?" else "Xóa tài khoản & dữ liệu?", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            if (isEnglish) "WARNING: All account information, love logs, and memories will be permanently deleted and cannot be recovered."
            else "CẢNH BÁO: Toàn bộ thông tin tài khoản, mật khẩu, nhật ký tình yêu và ảnh kỷ niệm sẽ bị xóa vĩnh viễn không thể phục hồi.",
            fontSize = 13.sp
          )
          Text(
            if (isEnglish) "Google Play Policy Compliance: You can also request account deletion via the Web link:"
            else "Tuân thủ chính sách bảo mật Google Play: Bạn cũng có thể yêu cầu xóa tài khoản qua liên kết Web:",
            fontSize = 12.sp,
            color = Color.Gray
          )
          TextButton(
            onClick = {
              uriHandler.openUri(com.example.config.LegalLinks.accountDeletion)
            },
            contentPadding = PaddingValues(0.dp)
          ) {
            Text(com.example.config.LegalLinks.accountDeletion, fontSize = 12.sp, color = Color(0xFF1976D2))
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteAccountConfirmDialog = false
            viewModel.deleteAccountAndData()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
          Text(if (isEnglish) "Permanently Delete" else "Xóa Vĩnh Viễn", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showDeleteAccountConfirmDialog = false }) {
          Text(if (isEnglish) "Cancel" else "Hủy Bỏ")
        }
      }
    )
  }

  if (showEditLoveTitleDialog) {
    EditLoveTitleDialog(
      currentTitle = loveTitle,
      isEnglish = isEnglish,
      onDismiss = { showEditLoveTitleDialog = false },
      onConfirm = { newTitle ->
        viewModel.updateLoveTitle(newTitle)
        showEditLoveTitleDialog = false
      }
    )
  }
}

/**
 * Pure UI: takes the already-resolved visibility decision and click handler as parameters,
 * rather than reading LocalContext/LocalActivityResultRegistryOwner itself — this is what
 * makes PrivacyOptionsRowTest able to verify the row's actual on-screen visibility without
 * needing to fake an Activity or a wrapped Context.
 */
@Composable
internal fun AdPrivacyOptionsRow(visible: Boolean, isEnglish: Boolean, onClick: () -> Unit) {
  if (!visible) return
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("settings_privacy_options_row")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        tint = Primary
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        if (isEnglish) "Ad Privacy Options" else "Quyền riêng tư quảng cáo",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

/**
 * Dedicated Language Selection Toggle Card in the Settings Tab.
 * Supports both immediate Segmented Button switching and quick Toggle Switch.
 */
@Composable
fun LanguageSelectionToggleCard(
  currentLanguage: AppLanguage,
  onLanguageChanged: (AppLanguage) -> Unit,
  onOpenDialog: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(Color(0xFFFF80AB).copy(alpha = 0.5f), Color(0xFFFFD1DC).copy(alpha = 0.5f))),
        shape = RoundedCornerShape(22.dp)
      )
      .testTag("language_selection_toggle")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header with Language Icon and Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(listOf(RoseGradientStart, RoseGradientMid))
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.Language,
              contentDescription = stringResource(R.string.settings_language_title),
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }

          Column {
            Text(
              text = stringResource(R.string.settings_language_title),
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = OnSurface
            )
            Text(
              text = stringResource(R.string.settings_language_sub),
              fontSize = 11.5.sp,
              color = OnSurfaceVariant
            )
          }
        }

        // Active language badge
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Primary.copy(alpha = 0.12f),
          modifier = Modifier.clickable { onOpenDialog() }
        ) {
          Text(
            text = if (currentLanguage == AppLanguage.VI) "🇻🇳 VI" else "🇬🇧 EN",
            color = Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }

      // Segmented Language Toggle Selector
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFFF7F1F5))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Vietnamese Button
        LanguageToggleButton(
          title = stringResource(R.string.lang_vietnamese),
          isSelected = currentLanguage == AppLanguage.VI,
          onClick = { onLanguageChanged(AppLanguage.VI) },
          modifier = Modifier
            .weight(1f)
            .testTag("lang_toggle_vi")
        )

        // English Button
        LanguageToggleButton(
          title = stringResource(R.string.lang_english),
          isSelected = currentLanguage == AppLanguage.EN,
          onClick = { onLanguageChanged(AppLanguage.EN) },
          modifier = Modifier
            .weight(1f)
            .testTag("lang_toggle_en")
        )
      }

      // Quick Switch Row for single-tap toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = stringResource(R.string.settings_language_toggle_label),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface
          )
          Text(
            text = if (currentLanguage == AppLanguage.EN) {
              stringResource(R.string.settings_language_current_en)
            } else {
              stringResource(R.string.settings_language_current_vi)
            },
            fontSize = 11.sp,
            color = Primary,
            fontWeight = FontWeight.SemiBold
          )
        }

        Switch(
          checked = currentLanguage == AppLanguage.EN,
          onCheckedChange = { isEnglishChecked ->
            onLanguageChanged(if (isEnglishChecked) AppLanguage.EN else AppLanguage.VI)
          },
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Primary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE0C4D3)
          ),
          modifier = Modifier.testTag("language_switch_toggle")
        )
      }
    }
  }
}

@Composable
private fun LanguageToggleButton(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val backgroundColor by animateColorAsState(
    targetValue = if (isSelected) Primary else Color.Transparent,
    animationSpec = tween(durationMillis = 200),
    label = "lang_toggle_bg"
  )
  val textColor by animateColorAsState(
    targetValue = if (isSelected) Color.White else OnSurface,
    animationSpec = tween(durationMillis = 200),
    label = "lang_toggle_text"
  )

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = backgroundColor,
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp, horizontal = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isSelected) {
        Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier
            .size(16.dp)
            .padding(end = 4.dp)
        )
      }
      Text(
        text = title,
        color = textColor,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.5.sp
      )
    }
  }
}

@Composable
fun SettingSwitchRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f)
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(PrimaryFixed.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(18.dp)
        )
      }
      Column {
        Text(
          text = title,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = OnSurface
        )
        Text(
          text = subtitle,
          fontSize = 11.sp,
          color = OnSurfaceVariant
        )
      }
    }

    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = Primary
      )
    )
  }
}

@Composable
fun SettingClickableRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f)
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(SurfaceContainerHigh),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(18.dp)
        )
      }
      Column {
        Text(
          text = title,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = OnSurface
        )
        Text(
          text = subtitle,
          fontSize = 11.sp,
          color = OnSurfaceVariant
        )
      }
    }

    Icon(
      imageVector = Icons.Filled.ChevronRight,
      contentDescription = null,
      tint = OnSurfaceVariant,
      modifier = Modifier.size(18.dp)
    )
  }
}
