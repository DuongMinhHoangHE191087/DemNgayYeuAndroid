package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Màn hình hiệu ứng mở ứng dụng lãng mạn (App Opening / Entrance Screen)
 * Với nhịp tim đập mượt mà, vòng hào quang lan tỏa (ripple aura rings)
 * và chuyển tiếp siêu mượt khi vào ứng dụng.
 */
@Composable
fun AppOpeningSplashScreen(
  modifier: Modifier = Modifier,
  isEnglish: Boolean = false,
  onFinished: () -> Unit
) {
  var isEntered by remember { mutableStateOf(false) }

  // Tự động kết thúc sau 1.9s, hoặc chạm để bỏ qua ngay
  LaunchedEffect(Unit) {
    isEntered = true
    delay(1900)
    onFinished()
  }

  // 1. Hiệu ứng nhịp đập trái tim (Lub-Dub Heartbeat)
  val infiniteTransition = rememberInfiniteTransition(label = "splash_heart_beat")
  val heartScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1300
        1.0f at 0
        1.24f at 180 using FastOutSlowInEasing // Nhịp 1
        1.06f at 340 using FastOutSlowInEasing
        1.18f at 500 using FastOutSlowInEasing // Nhịp 2
        1.0f at 700 using FastOutSlowInEasing
        1.0f at 1300
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "heart_scale"
  )

  // 2. Vòng hào quang tỏa sáng (Expanding Ripple Halo Rings)
  val haloScale1 by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.7f,
    animationSpec = infiniteRepeatable(
      animation = tween(1300, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "halo_scale_1"
  )
  val haloAlpha1 by infiniteTransition.animateFloat(
    initialValue = 0.45f,
    targetValue = 0.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1300, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "halo_alpha_1"
  )

  val haloScale2 by infiniteTransition.animateFloat(
    initialValue = 0.7f,
    targetValue = 1.5f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1300
        0.7f at 0
        0.7f at 200
        1.5f at 1300 using LinearEasing
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "halo_scale_2"
  )
  val haloAlpha2 by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.0f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1300
        0.35f at 0
        0.35f at 200
        0.0f at 1300 using LinearEasing
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "halo_alpha_2"
  )

  // 3. Fade-in và trượt nhẹ cho logo và tiêu đề
  val contentAlpha by animateFloatAsState(
    targetValue = if (isEntered) 1f else 0f,
    animationSpec = tween(650, easing = FastOutSlowInEasing),
    label = "content_alpha"
  )
  val contentOffsetY by animateFloatAsState(
    targetValue = if (isEntered) 0f else 28f,
    animationSpec = tween(650, easing = FastOutSlowInEasing),
    label = "content_offset_y"
  )

  // Nền gradient lãng mạn
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFFFFF0F5),
            Color(0xFFFFE3EE),
            Color(0xFFFFD4E5)
          )
        )
      )
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onFinished
      )
      .testTag("app_opening_splash"),
    contentAlignment = Alignment.Center
  ) {
    // Các đốm ánh sáng mơ màng lơ lửng phía nền
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        color = Color(0xFFFF80AB).copy(alpha = 0.12f),
        radius = size.minDimension * 0.45f,
        center = Offset(size.width * 0.2f, size.height * 0.25f)
      )
      drawCircle(
        color = Color(0xFFFF4081).copy(alpha = 0.08f),
        radius = size.minDimension * 0.5f,
        center = Offset(size.width * 0.85f, size.height * 0.75f)
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .graphicsLayer {
          alpha = contentAlpha
          translationY = contentOffsetY
        }
        .padding(horizontal = 24.dp)
    ) {
      // Vòng tròn trung tâm với hào quang nhịp đập
      Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
      ) {
        // Halo Ring 1
        Box(
          modifier = Modifier
            .size(110.dp)
            .scale(haloScale1)
            .alpha(haloAlpha1)
            .background(
              color = Color(0xFFFF4081).copy(alpha = 0.35f),
              shape = CircleShape
            )
        )

        // Halo Ring 2
        Box(
          modifier = Modifier
            .size(110.dp)
            .scale(haloScale2)
            .alpha(haloAlpha2)
            .background(
              color = Color(0xFFFF80AB).copy(alpha = 0.25f),
              shape = CircleShape
            )
        )

        // Main Heart Emblem
        Box(
          modifier = Modifier
            .size(86.dp)
            .scale(heartScale)
            .shadow(
              elevation = 16.dp,
              shape = CircleShape,
              spotColor = Color(0xFFFF2D75).copy(alpha = 0.6f),
              ambientColor = Color(0xFFFF4081).copy(alpha = 0.4f)
            )
            .background(
              brush = Brush.linearGradient(
                colors = listOf(
                  Color(0xFFFF2D75),
                  Color(0xFFE91E63),
                  Color(0xFFD81B60)
                )
              ),
              shape = CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "InLove Logo",
            tint = Color.White,
            modifier = Modifier.size(46.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Logo Name "InLove"
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = "InLove",
          fontSize = 36.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 1.5.sp,
          color = Color(0xFF880E4F)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Tagline lãng mạn ngắn gọn
      Text(
        text = if (isEnglish) "Cherish every sweet moment" else "Gắn kết từng khoảnh khắc yêu",
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFFAD1457).copy(alpha = 0.85f),
        letterSpacing = 0.5.sp
      )

      Spacer(modifier = Modifier.height(28.dp))

      // Chip ngày yêu tinh tế
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.75f),
        shadowElevation = 2.dp
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFE91E63),
            modifier = Modifier.size(13.dp)
          )
          Text(
            text = if (isEnglish) "Love Tracker • Smart Reminder" else "Đếm ngày yêu • Nhắc hẹn thông minh",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFC2185B)
          )
        }
      }
    }
  }
}
