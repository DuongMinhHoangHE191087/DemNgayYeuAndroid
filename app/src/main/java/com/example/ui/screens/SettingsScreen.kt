@file:Suppress("FunctionName")

package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
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
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel

import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun SettingsScreen(viewModel: InLoveViewModel) {
  var notificationEnabled by remember { mutableStateOf(true) }
  var soundEnabled by remember { mutableStateOf(true) }
  var biometricEnabled by remember { mutableStateOf(false) }

  val boyName by viewModel.boyName.collectAsState()
  val girlName by viewModel.girlName.collectAsState()
  val boyAvatarUrl by viewModel.boyAvatarUrl.collectAsState()
  val girlAvatarUrl by viewModel.girlAvatarUrl.collectAsState()
  val loveDays by viewModel.loveDays.collectAsState()

  val appLanguage by viewModel.appLanguage.collectAsState()
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current

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
  var showSetPinDialog by remember { mutableStateOf(false) }
  var showSecurityAuditLogsDialog by remember { mutableStateOf(false) }
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
    // 0. My Personal Profile Card (User Intent: user only adjusts their own profile, age and zodiac auto-calculated)
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color.White.copy(alpha = 0.95f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFB6C1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
            AsyncImage(
              model = currentUser.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
              contentDescription = "My Avatar",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFE91E63), CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = currentUser.effectiveDisplayName,
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (!currentUser.isProfileSetup) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFEBEE)
                  ) {
                    Text(
                      text = "VÔ DANH",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFFD32F2F),
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(2.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (currentUser.age > 0) {
                  Text(
                    text = "${currentUser.age} tuổi",
                    fontSize = 12.sp,
                    color = Color(0xFFE91E63),
                    fontWeight = FontWeight.SemiBold
                  )
                }
                if (currentUser.zodiac.isNotBlank()) {
                  Text(
                    text = "• Cung ${currentUser.zodiac}",
                    fontSize = 12.sp,
                    color = Color(0xFF880E4F),
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }

              Text(
                text = "Mã của bạn: ${currentUser.coupleCode}",
                fontSize = 11.sp,
                color = OnSurfaceVariant
              )
            }

            IconButton(
              onClick = { viewModel.openEditProfileDialog() },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Chỉnh sửa hồ sơ của tôi",
                tint = Primary
              )
            }
          }

          if (!currentUser.isProfileSetup) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFFF3E0),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "👉 Bạn chưa cập nhật hồ sơ. Hãy bấm vào đây để nhập tên & ngày sinh!",
                fontSize = 11.sp,
                color = Color(0xFFE65100),
                modifier = Modifier.padding(8.dp)
              )
            }
          }
        }
      }
    }
    // 1. Couple Profile Card
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = SurfaceContainerLowest.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { viewModel.openEditCoupleDialog() }
          .testTag("settings_couple_profile_card")
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Both avatars side by side
          Box(modifier = Modifier.size(68.dp)) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFF4DD0E1), CircleShape)
                .align(Alignment.TopStart)
            ) {
              AsyncImage(
                model = boyAvatarUrl,
                contentDescription = "Partner 1 Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFFF8A65), CircleShape)
                .align(Alignment.BottomEnd)
            ) {
              AsyncImage(
                model = girlAvatarUrl,
                contentDescription = "Partner 2 Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "$boyName & $girlName",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
              )
              Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.settings_profile_card_edit),
                tint = Primary,
                modifier = Modifier.size(16.dp)
              )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = stringResource(R.string.settings_sweet_days_format, loveDays),
              fontSize = 12.sp,
              color = Primary,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = stringResource(R.string.settings_db_secured),
              fontSize = 11.sp,
              color = OnSurfaceVariant
            )
          }

          Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = OnSurfaceVariant
          )
        }
      }
    }

    // 2. Set Love 1-1 Online Partner Card & Breakup Management
    item {
      Text(
        text = "TRẠNG THÁI TÌNH CẢM (SET LOVE 1-1)",
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
          // Partner header info
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            AsyncImage(
              model = partnerUser?.avatarUrl ?: "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200",
              contentDescription = "Partner Avatar",
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFFF4081), CircleShape),
              contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = partnerUser?.displayName ?: "Chưa có đối phương",
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (relationshipStatus == OnlineStatus.COUPLED) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                  Text(
                    text = if (relationshipStatus == OnlineStatus.COUPLED) "COUPLED" else "CHƯA GHÉP ĐÔI",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (relationshipStatus == OnlineStatus.COUPLED) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                  )
                }
              }

              val loveSince = activeRelationship?.startDateText?.ifBlank { null }
              val partnerSubtitle = if (partnerUser != null) {
                if (!loveSince.isNullOrBlank()) "Mã: ${partnerUser!!.coupleCode} • Yêu từ $loveSince" else "Mã: ${partnerUser!!.coupleCode}"
              } else {
                "Mã của bạn: ${currentUser.coupleCode}"
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
                    text = "Đối phương đã gửi yêu cầu hủy Set Love!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFB71C1C)
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Bạn có đồng ý hủy ghép đôi và trở về trạng thái Độc thân?",
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
                    Text("Từ chối chia tay", fontSize = 12.sp)
                  }
                  Button(
                    onClick = { viewModel.confirmBreakup() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("confirm_breakup_button")
                  ) {
                    Text("Xác nhận chia tay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Cưỡng chế hủy sau 7-14 ngày nếu một bên không phản hồi.",
                  fontSize = 10.sp,
                  color = Color.Gray,
                  modifier = Modifier.clickable { viewModel.forceBreakup() }
                )
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
                  text = "⏳ Đang chờ đối phương xác nhận yêu cầu hủy Set Love...",
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
                    Text("Rút lại yêu cầu", fontSize = 12.sp)
                  }
                  Button(
                    onClick = { viewModel.forceBreakup() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Text("Cưỡng chế hủy ngay", fontSize = 12.sp)
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
                    text = "Lời mời ghép đôi từ ${incomingInvite!!.effectiveSenderName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF880E4F)
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Đề xuất ngày bắt đầu: ${incomingInvite!!.proposedStartDateText.ifEmpty { "Chưa đặt" }}",
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
                  Text("Kiểm tra danh tính & Xác nhận ngày yêu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Action Buttons: Open Pairing Screen or Request Breakup
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
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
              Text("Trang Ghép Đôi", fontSize = 12.sp)
            }

            if (relationshipStatus == OnlineStatus.COUPLED && !isPendingBreakup) {
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
                Text("Hủy Set Love", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    // 2.5. VIP Subscription Banner & Entry Point to Paywall
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isVip) Color(0xFFFFF8E1) else Color(0xFFFFF0F5)
          ),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isVip) Color(0xFFFFD54F) else Color(0xFFF48FB1)
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
                .background(if (isVip) Color(0xFFFFB300) else Primary),
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
                text = if (isVip) "Thành Viên InLove VIP 👑" else "Nâng Cấp Gói VIP Tình Yêu ✨",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isVip) Color(0xFFB78103) else Primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = if (isVip) "Không quảng cáo • Cloud lưu trữ • AI không giới hạn"
                       else "Tắt sạch quảng cáo • Thử miễn phí 3 ngày gói Năm",
                fontSize = 12.sp,
                color = OnSurfaceVariant
              )
            }
            Icon(
              imageVector = Icons.Filled.ChevronRight,
              contentDescription = null,
              tint = if (isVip) Color(0xFFFFB300) else Primary
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
            text = "Quản lý & Hủy gói cước trên Google Play",
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

    // 3. Settings Group: Quản Lý Hồ Sơ & Kỷ Niệm
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
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.settings_edit_profile_title),
            subtitle = stringResource(R.string.settings_edit_profile_sub),
            onClick = { viewModel.openEditCoupleDialog() }
          )

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
              notificationEnabled = it
              viewModel.showToast(if (it) notifEnabledToast else notifDisabledToast)
            }
          )

          SettingSwitchRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = stringResource(R.string.settings_notif_sound_title),
            subtitle = stringResource(R.string.settings_notif_sound_sub),
            checked = soundEnabled,
            onCheckedChange = { soundEnabled = it }
          )
        }
      }
    }

    // 6. Settings Group: Dữ Liệu & Bảo Mật
    item {
      Text(
        text = "Tài Khoản & Bảo Mật Nâng Cao",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(8.dp))

      val currentAccount = (authState as? com.example.data.repository.AuthState.Authenticated)?.account

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          // Account Profile Header / Guest Mode Status
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
                  text = "Đã xác thực",
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
                    text = "Chế độ Khách (Offline)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF880E4F)
                  )
                }
                Text(
                  text = "Dữ liệu đếm ngày yêu và kỷ niệm của bạn đang được lưu trữ an toàn riêng tư trực tiếp trên máy.",
                  fontSize = 12.5.sp,
                  lineHeight = 18.sp,
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
                  Text("Đăng Nhập / Đăng Ký để Đồng Bộ Đám Mây", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
              }
            }
          }

          // PIN Protection Switch
          val isPinActive = currentAccount?.isPinEnabled == true
          SettingSwitchRow(
            icon = Icons.Filled.Lock,
            title = "Khóa ứng dụng bằng PIN",
            subtitle = if (isPinActive) "Đang bật mã PIN 4 số bảo vệ" else "Chưa bật bảo vệ PIN",
            checked = isPinActive,
            onCheckedChange = { enabled ->
              if (enabled && (currentAccount?.appPin.isNullOrEmpty())) {
                showSetPinDialog = true
              } else {
                viewModel.togglePinEnabled(enabled)
              }
            }
          )

          // Change PIN Button
          SettingClickableRow(
            icon = Icons.Filled.Key,
            title = "Cài đặt / Đổi mã PIN",
            subtitle = "Thiết lập mã 4 số riêng tư mở khóa nhanh",
            onClick = { showSetPinDialog = true }
          )

          // Account-specific options
          if (currentAccount != null) {
            // Change Password Button
            SettingClickableRow(
              icon = Icons.Filled.Shield,
              title = "Đổi mật khẩu tài khoản",
              subtitle = "Yêu cầu mật khẩu cũ & đánh giá độ mạnh",
              onClick = { showChangePasswordDialog = true }
            )

            // Security Audit Logs
            SettingClickableRow(
              icon = Icons.Filled.History,
              title = "Nhật ký bảo mật",
              subtitle = "Xem lịch sử đăng nhập, cảnh báo thử sai và đổi mật khẩu",
              onClick = { showSecurityAuditLogsDialog = true }
            )
          }

          // Lock App Now (if PIN enabled)
          if (isPinActive) {
            SettingClickableRow(
              icon = Icons.Filled.Lock,
              title = "Khóa ứng dụng ngay",
              subtitle = "Yêu cầu nhập mã PIN khi dùng tiếp",
              onClick = { viewModel.lockApp() }
            )
          }

          // Sync Enriched Cloud Presets from Firestore
          SettingClickableRow(
            icon = Icons.Filled.CloudSync,
            title = "Đồng bộ dữ liệu Cloud Firestore",
            subtitle = "Làm mới gợi ý quà tặng, huy hiệu và mốc kỷ niệm",
            onClick = { viewModel.syncCloudData() }
          )

          SettingClickableRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_sub),
            onClick = { viewModel.showToast(aboutToast) }
          )

          if (currentAccount != null) {
            Spacer(modifier = Modifier.height(12.dp))

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
              Text("Đăng Xuất Tài Khoản (Về Chế Độ Khách)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
              Text("Xóa Vĩnh Viễn Tài Khoản & Dữ Liệu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        Text(text = "Gửi yêu cầu hủy Set Love?", fontWeight = FontWeight.Bold)
      },
      text = {
        Text("Hệ thống sẽ gửi thông báo hủy ghép đôi đến ${partnerUser?.displayName ?: "đối phương"}. Khi đối phương đồng ý (hoặc sau thời gian chờ), trạng thái sẽ chuyển về Độc thân và tạm khóa kỷ niệm chung.")
      },
      confirmButton = {
        Button(
          onClick = {
            showConfirmBreakupDialog = false
            viewModel.requestBreakup()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
          Text("Gửi yêu cầu")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showConfirmBreakupDialog = false }) {
          Text("Hủy bỏ")
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
        Text("Đăng xuất tài khoản?", fontWeight = FontWeight.Bold)
      },
      text = {
        Text("Bạn sẽ cần đăng nhập lại bằng email và mật khẩu để tiếp tục sử dụng InLove.")
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirmDialog = false
            viewModel.logout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
          Text("Đăng xuất")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showLogoutConfirmDialog = false }) {
          Text("Hủy")
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
        Text("Xóa tài khoản & dữ liệu?", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "CẢNH BÁO: Toàn bộ thông tin tài khoản, mật khẩu, nhật ký tình yêu và ảnh kỷ niệm sẽ bị xóa vĩnh viễn không thể phục hồi.",
            fontSize = 13.sp
          )
          Text(
            "Tuân thủ chính sách bảo mật Google Play: Bạn cũng có thể yêu cầu xóa tài khoản qua liên kết Web:",
            fontSize = 12.sp,
            color = Color.Gray
          )
          TextButton(
            onClick = {
              uriHandler.openUri("https://inlove-app.web.app/delete-account")
            },
            contentPadding = PaddingValues(0.dp)
          ) {
            Text("https://inlove-app.web.app/delete-account", fontSize = 12.sp, color = Color(0xFF1976D2))
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
          Text("Xóa Vĩnh Viễn", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showDeleteAccountConfirmDialog = false }) {
          Text("Hủy Bỏ")
        }
      }
    )
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
