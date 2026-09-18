@file:Suppress("FunctionName")
package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Primary
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings
import com.example.ui.viewmodel.InLoveViewModel

/**
 * Màn hình Hướng Dẫn Sử Dụng (Onboarding Screen) khi mới tải ứng dụng.
 * Thiết kế chuẩn Material 3, Romantic Rose Light Mode, hỗ trợ song ngữ EN/VI mạch lạc.
 */
@Composable
fun OnboardingScreen(
  viewModel: InLoveViewModel,
  onFinishOnboarding: () -> Unit
) {
  val appLanguage by viewModel.appLanguage.collectAsState()
  val strings = remember(appLanguage) { LocalizedStrings.get(appLanguage) }

  var currentStep by remember { mutableIntStateOf(0) }
  val totalSteps = 5

  // Quick setup inputs for Step 5
  var boyNameInput by remember { mutableStateOf("") }
  var girlNameInput by remember { mutableStateOf("") }

  val infiniteTransition = rememberInfiniteTransition(label = "heart_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  val bgGradient = Brush.verticalGradient(
    listOf(
      Color(0xFFFFF7FA),
      Color(0xFFFFF0F5),
      Color(0xFFFDE8F1)
    )
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(bgGradient)
  ) {
    // Ambient romantic blur circles
    Box(
      modifier = Modifier
        .size(260.dp)
        .align(Alignment.TopEnd)
        .blur(90.dp)
        .background(Color(0xFFFF4081).copy(alpha = 0.12f), CircleShape)
    )
    Box(
      modifier = Modifier
        .size(240.dp)
        .align(Alignment.BottomStart)
        .blur(80.dp)
        .background(Color(0xFFFF80AB).copy(alpha = 0.10f), CircleShape)
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      // Top Navigation: Back button & Skip button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (currentStep > 0) {
          IconButton(
            onClick = { currentStep-- },
            modifier = Modifier
              .size(44.dp)
              .background(Color.White.copy(alpha = 0.9f), CircleShape)
              .border(1.dp, Color(0xFFFFD1E7), CircleShape)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = strings.onboardingBtnBack,
              tint = Color(0xFF4A1934)
            )
          }
        } else {
          Spacer(modifier = Modifier.size(44.dp))
        }

        // Step Dots Indicator
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            Box(
              modifier = Modifier
                .height(8.dp)
                .width(if (isActive) 24.dp else 8.dp)
                .clip(CircleShape)
                .background(
                  if (isActive) Brush.horizontalGradient(listOf(RoseGradientStart, RoseGradientMid))
                  else Brush.horizontalGradient(listOf(Color(0xFFFFD1E7), Color(0xFFFFD1E7)))
                )
            )
          }
        }

        // Skip Button
        TextButton(
          onClick = {
            viewModel.completeFirstLaunch()
            onFinishOnboarding()
          }
        ) {
          Text(
            text = strings.onboardingBtnSkip,
            color = Color(0xFF8A2E5B),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Animated Step Content
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        AnimatedContent(
          targetState = currentStep,
          transitionSpec = {
            if (targetState > initialState) {
              (slideInHorizontally { width -> (width * 0.3f).toInt() } + fadeIn(tween(300)))
                .togetherWith(slideOutHorizontally { width -> (-width * 0.3f).toInt() } + fadeOut(tween(300)))
            } else {
              (slideInHorizontally { width -> (-width * 0.3f).toInt() } + fadeIn(tween(300)))
                .togetherWith(slideOutHorizontally { width -> (width * 0.3f).toInt() } + fadeOut(tween(300)))
            }
          },
          label = "onboarding_step"
        ) { step ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            when (step) {
              0 -> OnboardingStepWelcome(
                strings = strings,
                appLanguage = appLanguage,
                onLanguageChange = { lang -> viewModel.setLanguage(lang) },
                pulseScale = pulseScale
              )
              1 -> OnboardingStepCounter(
                strings = strings,
                loveDays = 100
              )
              2 -> OnboardingStepMemories(
                strings = strings
              )
              3 -> OnboardingStepReminders(
                strings = strings
              )
              4 -> OnboardingStepStart(
                strings = strings,
                boyName = boyNameInput,
                girlName = girlNameInput,
                onBoyNameChange = { boyNameInput = it },
                onGirlNameChange = { girlNameInput = it }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Bottom Action Button
      Button(
        onClick = {
          if (currentStep < totalSteps - 1) {
            currentStep++
          } else {
            // Save initial couple names if entered
            if (boyNameInput.isNotBlank() || girlNameInput.isNotBlank()) {
              val boy = if (boyNameInput.isNotBlank()) boyNameInput else "Anh"
              val girl = if (girlNameInput.isNotBlank()) girlNameInput else "Em"
              viewModel.saveCoupleProfile(
                boy = boy,
                boyBirth = "01/01/2000",
                boyAvatar = "",
                boyA = 25,
                boyZod = "Bạch Dương",
                girl = girl,
                girlBirth = "01/01/2002",
                girlAvatar = "",
                girlA = 23,
                girlZod = "Kim Ngưu",
                title = "Bên Nhau Trọn Đời",
                days = 1,
                anniversary = "14/02/2024"
              )
            }
            viewModel.completeFirstLaunch()
            onFinishOnboarding()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.horizontalGradient(listOf(RoseGradientStart, RoseGradientMid, RoseGradientEnd)),
              shape = RoundedCornerShape(18.dp)
            ),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = if (currentStep == totalSteps - 1) strings.onboardingBtnStart else strings.onboardingBtnNext,
              color = Color.White,
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.5.sp
            )
            Icon(
              imageVector = if (currentStep == totalSteps - 1) Icons.Filled.AutoAwesome else Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

// ─── Step 1: Chào Mừng & Chọn Ngôn Ngữ ──────────────────────────────────────
@Composable
private fun OnboardingStepWelcome(
  strings: com.example.ui.util.AppStrings,
  appLanguage: AppLanguage,
  onLanguageChange: (AppLanguage) -> Unit,
  pulseScale: Float
) {
  // Lớn Icon trái tim
  Surface(
    shape = CircleShape,
    color = Color(0xFFFFEBF2),
    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF4081)),
    modifier = Modifier.size(100.dp)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = Color(0xFFE91E63),
        modifier = Modifier.size((52 * pulseScale).dp)
      )
    }
  }

  Spacer(modifier = Modifier.height(24.dp))

  Text(
    text = strings.onboardingWelcomeTitle,
    fontSize = 26.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Color(0xFF26071B),
    textAlign = TextAlign.Center
  )

  Spacer(modifier = Modifier.height(10.dp))

  Text(
    text = strings.onboardingWelcomeSub,
    fontSize = 14.5.sp,
    color = Color(0xFF7A2955),
    textAlign = TextAlign.Center,
    lineHeight = 22.sp,
    modifier = Modifier.padding(horizontal = 16.dp)
  )

  Spacer(modifier = Modifier.height(28.dp))

  // Box chọn ngôn ngữ nhanh (Quick Language Selector)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(Color.White)
      .border(1.5.dp, Color(0xFFFFD1E7), RoundedCornerShape(20.dp))
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Language,
        contentDescription = null,
        tint = Primary,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = strings.onboardingLangSelectTitle,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF26071B)
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      LanguagePill(
        title = "Tiếng Việt 🇻🇳",
        isSelected = appLanguage == AppLanguage.VI,
        onClick = { onLanguageChange(AppLanguage.VI) },
        modifier = Modifier.weight(1f)
      )
      LanguagePill(
        title = "English 🇬🇧",
        isSelected = appLanguage == AppLanguage.EN,
        onClick = { onLanguageChange(AppLanguage.EN) },
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun LanguagePill(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(if (isSelected) Color(0xFFFFF0F5) else Color(0xFFFBFBFB))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) Color(0xFFFF4081) else Color(0xFFE0E0E0),
        shape = RoundedCornerShape(14.dp)
      )
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = title,
      fontSize = 14.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) Color(0xFFC2185B) else Color(0xFF616161)
    )
  }
}

// ─── Step 2: Đếm Ngày Yêu ───────────────────────────────────────────────────
@Composable
private fun OnboardingStepCounter(
  strings: com.example.ui.util.AppStrings,
  loveDays: Int
) {
  OnboardingFeatureCard(
    icon = Icons.Filled.VolunteerActivism,
    title = strings.onboardingCounterTitle,
    subtitle = strings.onboardingCounterSub
  ) {
    // Interactive Counter Preview Card
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFFFFF2F7),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD1E7)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp)
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "🎉 100",
          fontSize = 42.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFFE91E63)
        )
        Text(
          text = strings.daysInLove.uppercase(),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = Color(0xFF8A2E5B)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "💕 Cùng nhau đi qua 100 ngày ngọt ngào!",
          fontSize = 13.sp,
          color = Color(0xFF4A1934),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

// ─── Step 3: Kho Kỷ Niệm ─────────────────────────────────────────────────────
@Composable
private fun OnboardingStepMemories(
  strings: com.example.ui.util.AppStrings
) {
  OnboardingFeatureCard(
    icon = Icons.Filled.PhotoLibrary,
    title = strings.onboardingMemoriesTitle,
    subtitle = strings.onboardingMemoriesSub
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFFFFF2F7),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD1E7)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp)
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF80AB), Color(0xFFFF4081)))),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
          )
        }
        Column {
          Text(
            text = "Lần đầu gặp gỡ • Phố Đi Bộ 📸",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF26071B)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Lưu trọn vẹn hình ảnh, địa điểm và dòng nhật ký yêu thương an toàn",
            fontSize = 12.sp,
            color = Color(0xFF7A2955),
            lineHeight = 17.sp
          )
        }
      }
    }
  }
}

// ─── Step 4: Nhắc Hẹn & Quà Tặng ────────────────────────────────────────────
@Composable
private fun OnboardingStepReminders(
  strings: com.example.ui.util.AppStrings
) {
  OnboardingFeatureCard(
    icon = Icons.Filled.CardGiftcard,
    title = strings.onboardingReminderTitle,
    subtitle = strings.onboardingReminderSub
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFFFFF2F7),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD1E7)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00C853),
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "Nhắc nhở trước 7 ngày, 3 ngày & 1 ngày",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF26071B)
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "AI đề xuất món quà bất ngờ & thư tình ngọt ngào",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF26071B)
          )
        }
      }
    }
  }
}

// ─── Step 5: Sẵn Sàng Bắt Đầu ───────────────────────────────────────────────
@Composable
private fun OnboardingStepStart(
  strings: com.example.ui.util.AppStrings,
  boyName: String,
  girlName: String,
  onBoyNameChange: (String) -> Unit,
  onGirlNameChange: (String) -> Unit
) {
  Surface(
    shape = CircleShape,
    color = Color(0xFFFFEBF2),
    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF4081)),
    modifier = Modifier.size(90.dp)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = Color(0xFFE91E63),
        modifier = Modifier.size(44.dp)
      )
    }
  }

  Spacer(modifier = Modifier.height(20.dp))

  Text(
    text = strings.onboardingStartTitle,
    fontSize = 24.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Color(0xFF26071B),
    textAlign = TextAlign.Center
  )

  Spacer(modifier = Modifier.height(8.dp))

  Text(
    text = strings.onboardingStartSub,
    fontSize = 14.sp,
    color = Color(0xFF7A2955),
    textAlign = TextAlign.Center,
    lineHeight = 20.sp,
    modifier = Modifier.padding(horizontal = 16.dp)
  )

  Spacer(modifier = Modifier.height(24.dp))

  // Couple name input card (Optional quick personalization)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFFFD1E7), RoundedCornerShape(20.dp))
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    OutlinedTextField(
      value = boyName,
      onValueChange = onBoyNameChange,
      label = { Text("Tên Bạn Trai / Partner 1") },
      placeholder = { Text("Ví dụ: Hoàng") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFFFF4081),
        unfocusedBorderColor = Color(0xFFFFD1E7)
      )
    )

    OutlinedTextField(
      value = girlName,
      onValueChange = onGirlNameChange,
      label = { Text("Tên Bạn Gái / Partner 2") },
      placeholder = { Text("Ví dụ: Mai") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFFFF4081),
        unfocusedBorderColor = Color(0xFFFFD1E7)
      )
    )
  }
}

@Composable
private fun OnboardingFeatureCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  content: @Composable () -> Unit
) {
  Surface(
    shape = CircleShape,
    color = Color(0xFFFFEBF2),
    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF4081)),
    modifier = Modifier.size(86.dp)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color(0xFFE91E63),
        modifier = Modifier.size(42.dp)
      )
    }
  }

  Spacer(modifier = Modifier.height(22.dp))

  Text(
    text = title,
    fontSize = 24.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Color(0xFF26071B),
    textAlign = TextAlign.Center
  )

  Spacer(modifier = Modifier.height(10.dp))

  Text(
    text = subtitle,
    fontSize = 14.sp,
    color = Color(0xFF7A2955),
    textAlign = TextAlign.Center,
    lineHeight = 21.sp,
    modifier = Modifier.padding(horizontal = 16.dp)
  )

  Spacer(modifier = Modifier.height(20.dp))

  content()
}
