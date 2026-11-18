@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.model.OnlineStatus
import com.example.ui.components.AppDialog
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.animateDialogEntrance
import com.example.ui.components.pressClickable
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.AppLanguage
import com.example.ui.util.Birthday
import com.example.ui.util.ProfileUtils
import com.example.ui.util.displayPartnerName
import com.example.ui.viewmodel.InLoveViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditCoupleDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  AppDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    EditCoupleDialogContent(viewModel, onDismiss)
  }
}

@Composable
fun CoupledProfileSummary(
  partnerName: String,
  partnerBirthday: String,
  partnerAvatarUrl: String,
  anniversary: String,
  isEnglish: Boolean
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    colors = CardDefaults.cardColors(containerColor = InLoveColors.SoftBlushCard),
    border = BorderStroke(1.dp, InLoveColors.BorderSoftPink),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("coupled_profile_summary")
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(
          url = partnerAvatarUrl,
          contentDescription = partnerName,
          modifier = Modifier
            .size(56.dp)
            .border(2.dp, Primary, CircleShape)
        )
        Column {
          Text(partnerName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = InLoveColors.RoseHighlight)
          if (partnerBirthday.isNotBlank()) {
            Text(
              if (isEnglish) "Birthday: $partnerBirthday" else "Ngày sinh: $partnerBirthday",
              fontSize = 12.sp,
              color = TextSubtlePlum
            )
          }
          if (anniversary.isNotBlank()) {
            Text(
              if (isEnglish) "Together since: $anniversary" else "Yêu nhau từ: $anniversary",
              fontSize = 12.sp,
              color = TextSubtlePlum
            )
          }
        }
      }
      Text(
        if (isEnglish) "Your partner updates their own info; it syncs automatically." else "Thông tin người ấy do người ấy tự cập nhật và được đồng bộ tự động.",
        fontSize = 12.sp,
        color = TextSubtlePlum
      )
    }
  }
}

@Composable
fun EditCoupleDialogContent(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val currentUser by viewModel.currentOnlineUser.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val incomingInvite by viewModel.incomingInvite.collectAsState()
  val outgoingInvite by viewModel.outgoingInvite.collectAsState()
  val anniversaryDate by viewModel.anniversaryDate.collectAsState()
  val girlName by viewModel.girlName.collectAsState()
  val girlBirthDate by viewModel.girlBirthDate.collectAsState()
  val girlAvatarUrl by viewModel.girlAvatarUrl.collectAsState()
  val loveTitle by viewModel.loveTitle.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val activeRel by viewModel.activeRelationship.collectAsState()

  val isEnglish = appLanguage == AppLanguage.EN
  val isCoupled = activeRel != null || relationshipStatus == OnlineStatus.COUPLED
  val todayFormatted = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date()) }

  var currentGirlName by remember(girlName) { mutableStateOf(girlName) }
  var currentGirlBirth by remember(girlBirthDate) { mutableStateOf(Birthday.normalize(girlBirthDate)) }
  var currentGirlAvatar by remember(girlAvatarUrl) { mutableStateOf(girlAvatarUrl) }
  val currentLoveTitle by remember(loveTitle) { mutableStateOf(loveTitle) }
  var currentAnniversary by remember(anniversaryDate) {
    mutableStateOf(anniversaryDate.takeUnless { it == "Hôm nay" }.orEmpty())
  }

  val girlAge by remember(currentGirlBirth) { derivedStateOf { ProfileUtils.calculateAge(currentGirlBirth) } }
  val girlZodiac by remember(currentGirlBirth) {
    derivedStateOf { if (currentGirlBirth.isBlank()) "" else ProfileUtils.calculateZodiac(currentGirlBirth).first }
  }
  val calculatedOfflineDays by remember(currentAnniversary) {
    derivedStateOf { ProfileUtils.calculateLoveDays(currentAnniversary) }
  }

  val girlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let { currentGirlAvatar = viewModel.saveLocalAvatar(it, isPartner = true) }
  }
  val partnerCaptureFile = remember { File(context.cacheDir, "camera/partner_avatar_capture.jpg") }
  val girlCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
    if (saved) {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", partnerCaptureFile)
      currentGirlAvatar = viewModel.saveLocalAvatar(uri, isPartner = true)
    }
  }

  var activeTab by remember { mutableIntStateOf(0) }
  var showPartnerPhotoActionPicker by remember { mutableStateOf(false) }

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

  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    border = BorderStroke(1.dp, InLoveColors.BorderRose),
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .graphicsLayer {
                scaleX = headerHeartScale
                scaleY = headerHeartScale
              }
              .background(
                brush = Brush.linearGradient(listOf(Secondary, Color(0xFFD81B60))),
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
          }
          Text(
            text = if (isEnglish) "Couple" else "Cặp đôi",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = InLoveColors.RoseHighlight
          )
        }
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(MinTouchBox)
            .clip(CircleShape)
            .pressClickable(onClick = onDismiss)
            .testTag("btn_close_edit_couple")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = if (isEnglish) "Close" else "Đóng",
            tint = TextSubtlePlum
          )
        }
      }

      CoupleDialogTabsRow(
        activeTab = activeTab,
        isEnglish = isEnglish,
        onSelectTab = { activeTab = it }
      )

      AnimatedContent(
        targetState = activeTab,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        label = "couple_tabs_content"
      ) { currentTab ->
        if (currentTab == 0) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isCoupled) {
              CoupledProfileSummary(
                partnerName = displayPartnerName(partnerUser?.effectiveDisplayName.orEmpty().ifBlank { girlName }, isEnglish),
                partnerBirthday = partnerUser?.birthDate.orEmpty().ifBlank { girlBirthDate },
                partnerAvatarUrl = partnerUser?.avatarUrl.orEmpty().ifBlank { girlAvatarUrl },
                anniversary = anniversaryDate,
                isEnglish = isEnglish
              )
            } else {
              OfflinePartnerProfileCard(
                girlName = currentGirlName,
                girlBirth = currentGirlBirth,
                girlAvatar = currentGirlAvatar,
                showPhotoPicker = showPartnerPhotoActionPicker,
                isEnglish = isEnglish,
                onTogglePhotoPicker = { showPartnerPhotoActionPicker = !showPartnerPhotoActionPicker },
                onClosePhotoPicker = { showPartnerPhotoActionPicker = false },
                onNameChange = { currentGirlName = it },
                onBirthChange = { currentGirlBirth = it },
                onCameraClick = {
                  showPartnerPhotoActionPicker = false
                  partnerCaptureFile.parentFile?.mkdirs()
                  girlCameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", partnerCaptureFile))
                },
                onGalleryClick = {
                  showPartnerPhotoActionPicker = false
                  girlLauncher.launch("image/*")
                },
                onRemoveAvatar = {
                  showPartnerPhotoActionPicker = false
                  currentGirlAvatar = ""
                }
              )

              Card(
                shape = RoundedCornerShape(InLoveRadii.Chip),
                colors = CardDefaults.cardColors(containerColor = InLoveColors.LavenderCardBg),
                border = BorderStroke(1.dp, InLoveColors.LavenderBorder),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  InLoveDatePickerField(
                    value = currentAnniversary,
                    onValueChange = { currentAnniversary = it },
                    label = if (isEnglish) "Love start date" else "Ngày bắt đầu yêu",
                    dialogTitle = if (isEnglish) "Select start date" else "Chọn ngày bắt đầu yêu",
                    quickPresets = DatePickerPresets.relationshipStartDatePresets(isEnglish),
                    isEnglish = isEnglish,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "input_anniversary_date",
                    helperText = if (currentAnniversary.isNotBlank()) {
                      if (isEnglish) "✨ $calculatedOfflineDays days together 💕" else "✨ $calculatedOfflineDays ngày bên nhau 💕"
                    } else null
                  )
                }
              }

              PrimaryGradientButton(
                text = if (isEnglish) "Save" else "Lưu",
                onClick = {
                  viewModel.saveCoupleProfile(
                    girl = currentGirlName,
                    girlBirth = if (currentGirlBirth == Birthday.normalize(girlBirthDate)) girlBirthDate else currentGirlBirth,
                    girlAvatar = currentGirlAvatar,
                    girlA = girlAge,
                    girlZod = girlZodiac,
                    title = currentLoveTitle,
                    days = if (currentAnniversary.isBlank()) 0 else calculatedOfflineDays,
                    anniversary = currentAnniversary
                  )
                },
                icon = Icons.Default.Favorite,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = MinTouchBox),
                testTag = "btn_save_couple_offline"
              )
            }
          }
        } else {
          CouplePairingTabContent(
            context = context,
            viewModel = viewModel,
            currentUser = currentUser,
            partnerUser = partnerUser,
            relationshipStatus = relationshipStatus,
            incomingInvite = incomingInvite,
            outgoingInvite = outgoingInvite,
            girlName = girlName,
            girlAvatarUrl = girlAvatarUrl,
            todayFormatted = todayFormatted,
            anniversaryDate = anniversaryDate,
            isEnglish = isEnglish,
            onDismiss = onDismiss
          )
        }
      }
    }
  }
}
