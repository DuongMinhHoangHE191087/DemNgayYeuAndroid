@file:Suppress("FunctionName")

package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.example.alarm.AlarmNotificationScheduler
import com.example.ui.util.findActivity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.components.animateDialogEntrance
import com.example.ui.components.AppAlertDialog
import com.example.ui.designsystem.InLoveTypography
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.MemoryPrivacyNotice
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.OnlineStatus
import com.example.data.model.RelationshipStatus
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
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

  val loveTitle by viewModel.loveTitle.collectAsState()
  var showEditLoveTitleDialog by remember { mutableStateOf(false) }

  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == AppLanguage.EN
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  // The permission is granted on a system screen, so it is read again each time the app comes back to the front.
  var exactGranted by remember { mutableStateOf(AlarmNotificationScheduler.exactAlarmsGranted(context)) }
  LifecycleResumeEffect(Unit) {
    exactGranted = AlarmNotificationScheduler.exactAlarmsGranted(context)
    onPauseOrDispose {}
  }

  // Online 1-1 Set Love States
  val currentUser by viewModel.currentOnlineUser.collectAsState()
  val boyAvatarUrl by viewModel.boyAvatarUrl.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val isVip by viewModel.isVip.collectAsState()

  // Account & Security States
  val authState by viewModel.authState.collectAsState()
  var showChangePasswordDialog by remember { mutableStateOf(false) }
  val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
  var facebookLinked by remember { mutableStateOf(viewModel.authRepo.isFacebookLinked) }
  var showSetPinDialog by remember { mutableStateOf(false) }
  var showSecurityAuditLogsDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }

  val notifEnabledToast = stringResource(R.string.settings_notif_enabled_toast)
  val notifDisabledToast = stringResource(R.string.settings_notif_disabled_toast)

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

    // 2. Language Selection Toggle (Primary Feature Highlight)
    item {
      LanguageSelectionToggleCard(
        currentLanguage = appLanguage,
        onLanguageChanged = { newLang -> viewModel.setLanguage(newLang) }
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
        TextButton(
          onClick = { uriHandler.openUri(com.example.config.LegalLinks.privacyPolicy) },
          colors = ButtonDefaults.textButtonColors(contentColor = Primary)
        ) {
          Text(stringResource(R.string.settings_legal_privacy), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(
          onClick = { uriHandler.openUri(com.example.config.LegalLinks.termsOfService) },
          colors = ButtonDefaults.textButtonColors(contentColor = Primary)
        ) {
          Text(stringResource(R.string.settings_legal_terms), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
      }
    }

    // 2.5. VIP Subscription Banner & Entry Point to Paywall
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = if (isVip) VipGoldContainer else SurfaceContainerLow),
          border = androidx.compose.foundation.BorderStroke(1.dp, if (isVip) VipGoldBorder else UpsellBorderPink),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchBox)
            .clickable { viewModel.setVipDialogVisible(true) }
            .testTag("settings_vip_upgrade_card")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Filled.WorkspacePremium,
              contentDescription = null,
              tint = if (isVip) VipGold else Primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isVip) {
                  if (isEnglish) "InLove Pro Member" else "Thành viên InLove Pro"
                } else {
                  if (isEnglish) "Upgrade to InLove Pro" else "Nâng cấp InLove Pro"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (isVip) VipGoldText else Primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = if (isVip) {
                  if (isEnglish) "Ad-free experience" else "Bỏ toàn bộ quảng cáo"
                } else {
                  if (isEnglish) "Ad-free experience · 3-day free trial" else "Bỏ toàn bộ quảng cáo · Dùng thử 3 ngày"
                },
                fontSize = 12.sp,
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

        // Nút Quản Lý & Hủy Gói Thuê Bao (Google Play Policy Bắt Buộc)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              uriHandler.openUri(com.example.config.LegalLinks.MANAGE_SUBSCRIPTIONS)
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Color(0xFF8A2E5B),
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isEnglish) "Manage subscription" else "Quản lý gói đăng ký",
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

    // 4. Settings Group: Giao Diện & Hình Nền
    item {
      Text(
        text = stringResource(R.string.settings_section_appearance),
        style = InLoveTypography.SectionHeader.copy(fontSize = 15.5.sp, color = Primary)
      )
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        style = InLoveTypography.SectionHeader.copy(fontSize = 15.5.sp, color = Primary)
      )
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (!exactGranted) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchBox)
                .clickable {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                      context.findActivity()?.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                      )
                    } catch (e: ActivityNotFoundException) {
                    }
                  }
                },
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.settings_exact_alarm_title),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = OnSurface
                )
                Text(
                  text = stringResource(R.string.settings_exact_alarm_sub),
                  fontSize = 11.sp,
                  color = OnSurfaceVariant
                )
              }
              Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }
          }

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

    // Thông báo riêng tư kỷ niệm (T17): rút gọn mặc định trên Cài đặt
    item { MemoryPrivacyNotice(initiallyExpanded = false) }

    // 6. Settings Group: Dữ Liệu & Bảo Mật Nâng Cao (Security & Privacy Center)
    item {
      Text(
        text = if (isEnglish) "Account" else "Tài Khoản",
        style = InLoveTypography.SectionHeader.copy(fontSize = 15.5.sp, color = Primary)
      )
      Spacer(modifier = Modifier.height(8.dp))

      val currentAccount = (authState as? com.example.data.repository.AuthState.Authenticated)?.account
      val isPinActive = currentAccount?.isPinEnabled == true

      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFD1DC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {

          // --- Account Profile Header / Guest Mode Status ---
          if (currentAccount != null) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchBox).clickable(onClickLabel = if (isEnglish) "Edit profile" else "Sửa hồ sơ", role = Role.Button) { viewModel.openEditProfileDialog() }.testTag("settings_profile_row")
            ) {
              ProfileAvatar(url = currentUser.avatarUrl.ifBlank { boyAvatarUrl }, contentDescription = null, modifier = Modifier.size(44.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = currentUser.effectiveDisplayName.ifBlank { currentAccount.displayName },
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp,
                  color = OnSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = currentAccount.email,
                  fontSize = 12.sp,
                  color = OnSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = if (isEnglish) "Verified" else "Đã xác thực",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
              )
              Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
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
                    text = if (isEnglish) "Guest mode" else "Chế độ khách",
                    style = InLoveTypography.SectionHeader.copy(fontSize = 16.sp, color = Color(0xFF880E4F))
                  )
                }
                PrimaryGradientButton(
                  text = if (isEnglish) "Sign In to Sync" else "Đăng nhập để đồng bộ",
                  onClick = { viewModel.openAuthScreen() },
                  icon = Icons.Filled.CloudSync,
                  modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchBox)
                )
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
            } else if (currentAccount == null) {
              if (isEnglish) "Sign in to use PIN lock" else "Đăng nhập để dùng khóa PIN"
            } else {
              if (isEnglish) "Passcode protection disabled" else "Chưa bật bảo vệ PIN"
            },
            checked = isPinActive,
            onCheckedChange = { enabled ->
              // The PIN is stored on the account, so guests can't set one: send them to sign-in.
              if (currentAccount == null) {
                viewModel.openAuthScreen()
              } else if (enabled && currentAccount.appPin.isEmpty()) {
                showSetPinDialog = true
              } else {
                viewModel.togglePinEnabled(enabled)
              }
            }
          )

          if (isPinActive) {
            SettingClickableRow(
              icon = Icons.Filled.Key,
              title = if (isEnglish) "Change PIN" else "Đổi mã PIN",
              subtitle = if (isEnglish) "4-digit quick unlock" else "Mã 4 số mở khóa nhanh",
              onClick = { showSetPinDialog = true }
            )
          }

          // Biometric Authentication toggle removed: this app has no biometric (fingerprint/
          // face) integration anywhere — it only ever simulated turning one on, same false
          // claim already stripped from the VIP paywall copy elsewhere in this app. Re-add a
          // real toggle here only alongside an actual BiometricPrompt integration.

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

          if (currentAccount != null) {
            Spacer(modifier = Modifier.height(14.dp))

            // Logout Button
            SecondaryButton(
              text = if (isEnglish) "Sign out" else "Đăng xuất",
              onClick = { showLogoutConfirmDialog = true },
              icon = Icons.AutoMirrored.Filled.ExitToApp,
              isDestructive = true,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchBox),
              testTag = "btn_logout"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SecondaryButton(
              text = if (isEnglish) "Delete account" else "Xóa tài khoản",
              onClick = { showDeleteAccountConfirmDialog = true },
              icon = Icons.Default.DeleteForever,
              isDestructive = true,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchBox),
              testTag = "btn_delete_account"
            )
          }
        }
      }
    }
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
    AppAlertDialog(
      onDismissRequest = { showLogoutConfirmDialog = false },
      scrollableText = true,
      modifier = Modifier.animateDialogEntrance(),
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
        Text(if (isEnglish) "You'll need to sign in again." else "Bạn sẽ cần đăng nhập lại.")
      },
      confirmButton = {
        SecondaryButton(
          text = if (isEnglish) "Sign Out" else "Đăng xuất",
          onClick = {
            showLogoutConfirmDialog = false
            viewModel.logout()
          },
          isDestructive = true,
          filled = true,
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      },
      dismissButton = {
        SecondaryButton(
          text = if (isEnglish) "Cancel" else "Hủy",
          onClick = { showLogoutConfirmDialog = false },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      }
    )
  }

  // Delete Account Confirmation Dialog (Google Play Policy Compliance)
  if (showDeleteAccountConfirmDialog) {
    AppAlertDialog(
      onDismissRequest = { showDeleteAccountConfirmDialog = false },
      scrollableText = true,
      modifier = Modifier.animateDialogEntrance(),
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
            if (isEnglish) "All data will be permanently deleted."
            else "Toàn bộ dữ liệu sẽ bị xóa vĩnh viễn.",
            fontSize = 13.sp
          )
          Text(
            if (isEnglish) "Google Play Policy Compliance: You can also request account deletion via the Web link:"
            else "Tuân thủ chính sách bảo mật Google Play: Bạn cũng có thể yêu cầu xóa tài khoản qua liên kết Web:",
            fontSize = 12.sp,
            color = OnSurfaceVariant
          )
          TextButton(
            onClick = {
              uriHandler.openUri(com.example.config.LegalLinks.accountDeletion)
            },
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Primary)
          ) {
            Text(com.example.config.LegalLinks.accountDeletion, fontSize = 13.sp, fontWeight = FontWeight.Medium)
          }
        }
      },
      confirmButton = {
        SecondaryButton(
          text = if (isEnglish) "Permanently Delete" else "Xóa Vĩnh Viễn",
          onClick = {
            showDeleteAccountConfirmDialog = false
            viewModel.deleteAccountAndData()
          },
          isDestructive = true,
          filled = true,
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      },
      dismissButton = {
        SecondaryButton(
          text = if (isEnglish) "Cancel" else "Hủy Bỏ",
          onClick = { showDeleteAccountConfirmDialog = false },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
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

/** Language picker: one compact row, tap VI or EN. */
@Composable
fun LanguageSelectionToggleCard(
  currentLanguage: AppLanguage,
  onLanguageChanged: (AppLanguage) -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("language_selection_toggle")
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Language,
        contentDescription = null,
        tint = Primary,
        modifier = Modifier.size(22.dp)
      )
      Text(
        text = stringResource(R.string.settings_language_title),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFFF7F1F5))
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        LanguageToggleButton(
          title = "VI",
          isSelected = currentLanguage == AppLanguage.VI,
          onClick = { onLanguageChanged(AppLanguage.VI) },
          modifier = Modifier.testTag("lang_toggle_vi")
        )
        LanguageToggleButton(
          title = "EN",
          isSelected = currentLanguage == AppLanguage.EN,
          onClick = { onLanguageChanged(AppLanguage.EN) },
          modifier = Modifier.testTag("lang_toggle_en")
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
      .heightIn(min = MinTouchBox)
      .clip(RoundedCornerShape(10.dp))
      .clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .padding(vertical = 8.dp, horizontal = 14.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title,
        color = textColor,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
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
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MinTouchBox)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OnSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
      Column {
        Text(
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          text = title,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = OnSurface
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            fontSize = 11.5.sp,
            color = OnSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
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
      .heightIn(min = MinTouchBox)
      .clickable { onClick() }
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OnSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
      Column {
        Text(
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          text = title,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = OnSurface
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            fontSize = 11.5.sp,
            color = OnSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
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
