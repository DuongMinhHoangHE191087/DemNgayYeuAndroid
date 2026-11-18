@file:Suppress("FunctionName")
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.AnniversaryMemoriesWidget
import com.example.ui.components.LoveDaysCalculatorWidget
import com.example.ui.components.MilestoneBadgeDashboardCard
import com.example.ui.components.pressClickable
import com.example.ui.designsystem.InLoveLayoutGrid
import com.example.ui.screens.home.AmbientFloatingHeartsCanvasContent
import com.example.ui.screens.home.HomeCoupleProfileCard
import com.example.ui.screens.home.HomeCoupleReflectionsCard
import com.example.ui.screens.home.HomeDaysCounterHero
import com.example.ui.screens.home.HomeNextMilestoneCard
import com.example.ui.screens.home.HomeStatusBanners
import com.example.ui.screens.home.NextOccasionReadinessCard
import com.example.ui.theme.CardBorder
import com.example.ui.theme.MinTouchBox
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings
import com.example.ui.util.displayMeName
import com.example.ui.util.displayPartnerName
import com.example.ui.viewmodel.InLoveViewModel
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

const val CHERRY_BLOSSOM_BG =
  "https://images.unsplash.com/photo-1522383225653-ed111181a951?q=80&w=1080&auto=format&fit=crop"

@Composable
fun LoveHomeScreen(
  viewModel: InLoveViewModel,
  onNavigateToCalendar: () -> Unit,
  onNavigateToGifts: () -> Unit,
  onNavigateToMemories: () -> Unit = {}
) {
  val boyName by viewModel.boyName.collectAsState()
  val boyBirthDate by viewModel.boyBirthDate.collectAsState()
  val boyAge by viewModel.boyAge.collectAsState()
  val boyZodiac by viewModel.boyZodiac.collectAsState()
  val boyAvatarUrl by viewModel.boyAvatarUrl.collectAsState()

  val girlName by viewModel.girlName.collectAsState()
  val girlBirthDate by viewModel.girlBirthDate.collectAsState()
  val girlAge by viewModel.girlAge.collectAsState()
  val girlZodiac by viewModel.girlZodiac.collectAsState()
  val girlAvatarUrl by viewModel.girlAvatarUrl.collectAsState()

  val loveTitle by viewModel.loveTitle.collectAsState()
  val loveDays by viewModel.loveDays.collectAsState()
  val anniversaryDate by viewModel.anniversaryDate.collectAsState()

  val currentOnlineUser by viewModel.currentOnlineUser.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val coupleLoaded by viewModel.coupleLoaded.collectAsState()
  val incomingInvite by viewModel.incomingInvite.collectAsState()

  val selectedWallpaperUrl by viewModel.selectedWallpaperUrl.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()
  val nextReadiness by viewModel.nextOccasionReadiness.collectAsState()
  val isSacredCelebrationToday by viewModel.isSacredCelebrationToday.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val strings = LocalizedStrings.get(appLanguage)
  val isEnglish = appLanguage == AppLanguage.EN
  val meLabel = displayMeName(boyName, isEnglish)
  val partnerLabel = displayPartnerName(girlName, isEnglish)

  val scrollState = rememberScrollState()
  var showEditLoveTitleDialog by remember { mutableStateOf(false) }

  val nextMilestone = ((loveDays / 100) + 1) * 100
  val daysRemaining = (nextMilestone - loveDays).coerceAtLeast(1)
  val milestoneProgress = ((loveDays % 100).toFloat() / 100f).coerceIn(0.05f, 1f)

  val screenConfig = LocalConfiguration.current
  val isCompactScreen = screenConfig.screenWidthDp < 390 || screenConfig.screenHeightDp < 750
  val counterBoxSize = if (isCompactScreen) 198.dp else 222.dp
  val counterSurfaceSize = counterBoxSize - 22.dp
  val daysCountFontSize = if (isCompactScreen) 46.sp else 54.sp
  val daysCountLineHeight = if (isCompactScreen) 48.sp else 56.sp
  val daysTitleFontSize = if (isCompactScreen) 16.sp else 18.sp
  val daysInLoveLabelSize = if (isCompactScreen) 13.sp else 14.5.sp
  val avatarOuterSize = if (isCompactScreen) 62.dp else 70.dp
  val avatarInnerSize = avatarOuterSize - 4.dp
  val centerHeartSize = if (isCompactScreen) 44.dp else 50.dp

  val isMilestoneReached = loveDays > 0 && loveDays % 100 == 0
  LaunchedEffect(loveDays) {
    if (isMilestoneReached) viewModel.celebrateMilestoneAnniversary(loveDays)
  }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1.minutes)
      viewModel.refreshLoveDays()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    AsyncImage(
      model = selectedWallpaperUrl,
      contentDescription = "Romantic Wallpaper Background",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(Color(0x70FFF5F8), Color(0x55FFE4E1), Color(0x75FCE4EC), Color(0xF5FFF0F5))
          )
        )
    )
    AmbientFloatingHeartsCanvas(modifier = Modifier.fillMaxSize())

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = InLoveLayoutGrid.ScreenHorizontal, vertical = 4.dp)
        .padding(bottom = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 4.dp,
        modifier = Modifier
          .fillMaxWidth()
          .border(width = 1.2.dp, color = CardBorder, shape = RoundedCornerShape(20.dp))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          HomeQuickActionButton(
            icon = Icons.Filled.Wallpaper,
            label = strings.actionWallpaper,
            onClick = { viewModel.openWallpaperDialog() },
            testTag = "btn_quick_wallpaper",
            modifier = Modifier.weight(1f)
          )
          HomeQuickActionButton(
            icon = Icons.Filled.CameraAlt,
            label = strings.actionMemory,
            onClick = { viewModel.openMemoryDialog() },
            testTag = "btn_quick_memory",
            modifier = Modifier.weight(1f)
          )
          HomeQuickActionButton(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = strings.actionGuide,
            onClick = { viewModel.openGuideDialog() },
            testTag = "btn_quick_guide",
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      HomeStatusBanners(
        isOnline = isOnline,
        hasLocalOrOnlineName = currentOnlineUser.isProfileSetup || boyName.isNotBlank(),
        incomingInvite = incomingInvite,
        showPairBanner = shouldShowPairBanner(relationshipStatus, girlName, coupleLoaded),
        isEnglish = isEnglish,
        onOpenEditProfile = { viewModel.openEditProfileDialog() },
        onOpenPairingScreen = { viewModel.openPairingScreen() }
      )

      HomeDaysCounterHero(
        loveDays = loveDays,
        loveTitle = loveTitle,
        daysInLoveLabel = strings.daysInLove,
        isEnglish = isEnglish,
        counterBoxSize = counterBoxSize,
        counterSurfaceSize = counterSurfaceSize,
        daysTitleFontSize = daysTitleFontSize,
        daysCountFontSize = daysCountFontSize,
        daysCountLineHeight = daysCountLineHeight,
        daysInLoveLabelSize = daysInLoveLabelSize,
        onCounterClick = {
          viewModel.triggerFloatingHearts()
          val toastMsg = if (isEnglish) "$loveDays wonderful days together" else "Bên nhau $loveDays ngày thật ngọt ngào"
          viewModel.showToast(toastMsg)
        },
        onEditTitleClick = { showEditLoveTitleDialog = true }
      )

      Spacer(modifier = Modifier.height(14.dp))

      HomeCoupleProfileCard(
        currentOnlineUser = currentOnlineUser,
        partnerUser = partnerUser,
        relationshipStatus = relationshipStatus,
        boyName = boyName,
        boyBirthDate = boyBirthDate,
        boyAvatarUrl = boyAvatarUrl,
        boyAge = boyAge,
        boyZodiac = boyZodiac,
        girlName = girlName,
        girlBirthDate = girlBirthDate,
        girlAvatarUrl = girlAvatarUrl,
        girlAge = girlAge,
        girlZodiac = girlZodiac,
        meLabel = meLabel,
        partnerLabel = partnerLabel,
        isEnglish = isEnglish,
        avatarOuterSize = avatarOuterSize,
        avatarInnerSize = avatarInnerSize,
        centerHeartSize = centerHeartSize,
        onMyAvatarClick = { viewModel.openEditProfileDialog() },
        onHeartClick = { isCoupled ->
          viewModel.triggerFloatingHearts()
          if (isCoupled) {
            viewModel.showToast(if (isEnglish) "Heart connection: $meLabel & $partnerLabel" else "Kết nối $meLabel & $partnerLabel")
          } else {
            viewModel.showToast(if (isEnglish) "$meLabel & $partnerLabel together" else "Hai bạn $meLabel & $partnerLabel luôn gắn kết bên nhau")
          }
        },
        onCoupledPartnerClick = { viewModel.openPairingScreen() },
        onOfflinePartnerClick = { viewModel.openEditCoupleDialog() }
      )

      Spacer(modifier = Modifier.height(14.dp))

      LoveDaysCalculatorWidget(
        anniversaryDate = anniversaryDate,
        currentLoveDays = loveDays,
        boyName = meLabel,
        girlName = partnerLabel,
        language = appLanguage,
        onRecalculateAndSync = { viewModel.syncCalculatedDaysToHome(it) },
        onUpdateAnniversaryDate = { viewModel.setAnniversaryAndRecalculateDays(it) },
        onTriggerHearts = {
          viewModel.triggerFloatingHearts()
          val msg = if (appLanguage == AppLanguage.VI) {
            "Hai bạn $meLabel & $partnerLabel đã gắn bó $loveDays ngày yêu thương"
          } else {
            "$meLabel & $partnerLabel have been in love for $loveDays days"
          }
          viewModel.showToast(msg)
        },
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(14.dp))

      MilestoneBadgeDashboardCard(
        viewModel = viewModel,
        onOpenFullShowcase = { viewModel.openBadgeShowcase() },
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(14.dp))

      AnniversaryMemoriesWidget(
        viewModel = viewModel,
        onNavigateToFullAlbum = onNavigateToMemories,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(14.dp))

      HomeNextMilestoneCard(
        loveDays = loveDays,
        nextMilestone = nextMilestone,
        daysRemaining = daysRemaining,
        milestoneProgress = milestoneProgress,
        isMilestoneReached = isMilestoneReached,
        titleFormat = strings.nextMilestoneTitle,
        daysRemainingFormat = strings.daysRemainingFormat,
        progressTowardsLabel = strings.progressTowards,
        giftButtonLabel = strings.btnGiftIdeas,
        calendarButtonLabel = strings.btnAnniversaryCalendar,
        celebrationTitle = strings.milestoneCelebrationTitle,
        celebrationButtonLabel = strings.milestoneCelebrationBtn,
        onOpenGifts = onNavigateToGifts,
        onOpenCalendar = onNavigateToCalendar,
        onCelebrate = { viewModel.celebrateMilestoneAnniversary(if (isMilestoneReached) loveDays else nextMilestone) }
      )

      nextReadiness?.let { readiness ->
        Spacer(modifier = Modifier.height(14.dp))
        NextOccasionReadinessCard(
          readiness = readiness,
          isSacredCelebrationToday = isSacredCelebrationToday,
          isEnglish = isEnglish,
          onOpenGifts = onNavigateToGifts,
          onOpenCalendar = onNavigateToCalendar
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      HomeCoupleReflectionsCard(
        loveDays = loveDays,
        appLanguage = appLanguage,
        sectionTag = strings.quoteSectionTag,
        sectionTitle = strings.quoteSectionTitle,
        shuffleLabel = strings.btnShuffleQuote,
        sendLabel = strings.btnSendToPartner,
        onShuffle = {
          viewModel.showToast(if (appLanguage == AppLanguage.VI) "Đã chuyển sang lời nhắn tiếp theo" else "Next reflection loaded")
        },
        onSend = {
          viewModel.triggerFloatingHearts()
          viewModel.showToast(if (appLanguage == AppLanguage.VI) "Đã sẵn sàng gửi lời nhắn ngọt ngào tới $partnerLabel" else "Sweet love reflection ready for $partnerLabel")
        }
      )

      Spacer(modifier = Modifier.height(16.dp))
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
}

@Composable
fun HomeQuickActionButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = ""
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier
      .defaultMinSize(minWidth = MinTouchBox, minHeight = MinTouchBox)
      .clip(RoundedCornerShape(14.dp))
      .pressClickable(onClick = onClick)
      .padding(horizontal = 2.dp, vertical = 4.dp)
      .testTag(testTag)
  ) {
    Box(
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFFFFB6C1), Color(0xFFFF80AB))))
        .shadow(2.dp, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(17.dp))
    }
  }
}

@Composable
fun AmbientFloatingHeartsCanvas(modifier: Modifier = Modifier) =
  AmbientFloatingHeartsCanvasContent(modifier)
