package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LoveBadgeEntity
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Visual Milestone Tracker - Displays relationship milestones rewarded with digital badges
 * and heart-themed emblems based on days together.
 */

// Tier Colors and Styling Helper
data class BadgeTheme(
  val primaryColor: Color,
  val secondaryColor: Color,
  val gradient: List<Color>,
  val ribbonColor: Color,
  val glowColor: Color,
  val tierNameVi: String,
  val tierNameEn: String
)

fun getBadgeTheme(tier: String): BadgeTheme {
  return when (tier.uppercase()) {
    "BRONZE" -> BadgeTheme(
      primaryColor = Color(0xFF8D6E63),
      secondaryColor = Color(0xFFD7CCC8),
      gradient = listOf(Color(0xFFFFCCBC), Color(0xFFD7CCC8), Color(0xFFBCAAA4)),
      ribbonColor = Color(0xFFA1887F),
      glowColor = Color(0xFFFFAB91).copy(alpha = 0.4f),
      tierNameVi = "Huy Hiệu Đồng",
      tierNameEn = "Bronze Badge"
    )
    "SILVER" -> BadgeTheme(
      primaryColor = Color(0xFF546E7A),
      secondaryColor = Color(0xFFECEFF1),
      gradient = listOf(Color(0xFFCFD8DC), Color(0xFFECEFF1), Color(0xFF90A4AE)),
      ribbonColor = Color(0xFF78909C),
      glowColor = Color(0xFFB0BEC5).copy(alpha = 0.4f),
      tierNameVi = "Huy Hiệu Bạc",
      tierNameEn = "Silver Badge"
    )
    "GOLD" -> BadgeTheme(
      primaryColor = Color(0xFFFFA000),
      secondaryColor = Color(0xFFFFF8E1),
      gradient = listOf(Color(0xFFFFD54F), Color(0xFFFFF9C4), Color(0xFFFFB300)),
      ribbonColor = Color(0xFFFF8F00),
      glowColor = Color(0xFFFFD54F).copy(alpha = 0.5f),
      tierNameVi = "Huy Hiệu Vàng",
      tierNameEn = "Gold Badge"
    )
    "RUBY" -> BadgeTheme(
      primaryColor = Color(0xFFE91E63),
      secondaryColor = Color(0xFFFCE4EC),
      gradient = listOf(Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFC2185B)),
      ribbonColor = Color(0xFFAD1457),
      glowColor = Color(0xFFFF4081).copy(alpha = 0.5f),
      tierNameVi = "Huy Hiệu Hồng Ngọc",
      tierNameEn = "Ruby Badge"
    )
    "DIAMOND" -> BadgeTheme(
      primaryColor = Color(0xFF00ACC1),
      secondaryColor = Color(0xFFE0F7FA),
      gradient = listOf(Color(0xFF26C6DA), Color(0xFFE0F7FA), Color(0xFF0097A7)),
      ribbonColor = Color(0xFF00838F),
      glowColor = Color(0xFF00E5FF).copy(alpha = 0.5f),
      tierNameVi = "Huy Hiệu Kim Cương",
      tierNameEn = "Diamond Badge"
    )
    "COSMIC" -> BadgeTheme(
      primaryColor = Color(0xFF8E24AA),
      secondaryColor = Color(0xFFF3E5F5),
      gradient = listOf(Color(0xFFAB47BC), Color(0xFFEA80FC), Color(0xFF6A1B9A)),
      ribbonColor = Color(0xFF4A148C),
      glowColor = Color(0xFFE040FB).copy(alpha = 0.6f),
      tierNameVi = "Huy Hiệu Vũ Trụ",
      tierNameEn = "Cosmic Badge"
    )
    else -> BadgeTheme(
      primaryColor = Primary,
      secondaryColor = PrimaryContainer,
      gradient = listOf(Color(0xFFFF4081), Color(0xFFFF80AB)),
      ribbonColor = Primary,
      glowColor = Primary.copy(alpha = 0.4f),
      tierNameVi = "Huy Hiệu Trái Tim",
      tierNameEn = "Heart Badge"
    )
  }
}

/**
 * Resolves heart-themed vector icon based on iconType string
 */
fun getHeartIconVector(iconType: String): ImageVector {
  return when (iconType.lowercase()) {
    "sprout_heart" -> Icons.Filled.Spa
    "moon_heart" -> Icons.Filled.Nightlight
    "rose_heart" -> Icons.Filled.LocalFlorist
    "blossom_heart" -> Icons.Filled.AutoAwesome
    "crown_heart" -> Icons.Filled.WorkspacePremium
    "crystal_heart" -> Icons.Filled.Diamond
    "ring_heart" -> Icons.Filled.AllInclusive
    "trophy_heart" -> Icons.Filled.EmojiEvents
    "flame_heart" -> Icons.Filled.Whatshot
    "galaxy_heart" -> Icons.Filled.Stars
    "infinity_heart" -> Icons.Filled.AllInclusive
    "eternity_diamond" -> Icons.Filled.Diamond
    else -> Icons.Filled.Favorite
  }
}

/**
 * Custom Heart-Themed Digital Badge Composable with Glowing Ring & Layered Medallion
 */
@Composable
fun HeartBadgeMedallion(
  badge: LoveBadgeEntity,
  isUnlocked: Boolean,
  size: Dp = 80.dp,
  showGlow: Boolean = true,
  onClick: (() -> Unit)? = null
) {
  val theme = getBadgeTheme(badge.tier)
  val iconVector = getHeartIconVector(badge.iconType)

  val infiniteTransition = rememberInfiniteTransition(label = "badge_shimmer")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isUnlocked && showGlow) 1.04f else 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  val rotationAngle by infiniteTransition.animateFloat(
    initialValue = -3f,
    targetValue = if (isUnlocked && showGlow) 3f else 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(2200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "subtle_tilt"
  )

  Box(
    modifier = Modifier
      .size(size)
      .scale(pulseScale)
      .rotate(rotationAngle)
      .then(
        if (onClick != null) {
          Modifier
            .clip(CircleShape)
            .clickable { onClick() }
        } else Modifier
      ),
    contentAlignment = Alignment.Center
  ) {
    if (isUnlocked) {
      // Radiant Glowing Aura for Unlocked Badges
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(CircleShape)
          .drawBehind {
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  theme.glowColor,
                  theme.primaryColor.copy(alpha = 0.25f),
                  Color.Transparent
                )
              )
            )
          }
      )

      // Outer Medallion Frame with Metallic Gradient Border
      Surface(
        modifier = Modifier
          .size(size * 0.88f)
          .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = theme.primaryColor),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(2.5.dp, Brush.sweepGradient(theme.gradient))
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(theme.gradient)),
          contentAlignment = Alignment.Center
        ) {
          // Inner Heart Silhouette background
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(size * 0.7f)
          )

          // Center Foreground Badge Icon
          Icon(
            imageVector = iconVector,
            contentDescription = badge.titleVi,
            tint = theme.primaryColor,
            modifier = Modifier.size(size * 0.42f)
          )

          // Little Sparkle Indicator Top Right
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFD700),
            modifier = Modifier
              .size(size * 0.22f)
              .align(Alignment.TopEnd)
              .offset(x = (-4).dp, y = 4.dp)
          )
        }
      }

      // Bottom Ribbon Badge with Days Duration
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.ribbonColor,
        shadowElevation = 3.dp,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .offset(y = 2.dp)
      ) {
        Text(
          text = "${badge.targetDays}D",
          color = Color.White,
          fontSize = (size.value * 0.12f).coerceIn(9f, 12f).sp,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    } else {
      // Locked State - Frosted Slate Medallion with Lock
      Surface(
        modifier = Modifier
          .size(size * 0.84f)
          .shadow(elevation = 2.dp, shape = CircleShape),
        shape = CircleShape,
        color = Color(0xFFF0F0F3),
        border = BorderStroke(1.5.dp, Color(0xFFBDBDBD).copy(alpha = 0.7f))
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8EAF0)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = Color(0xFF9E9E9E).copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.55f)
          )

          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Locked",
            tint = Color(0xFF757575),
            modifier = Modifier.size(size * 0.32f)
          )
        }
      }

      // Bottom Pill showing duration
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF757575),
        shadowElevation = 1.dp,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .offset(y = 2.dp)
      ) {
        Text(
          text = "${badge.targetDays}D",
          color = Color.White,
          fontSize = (size.value * 0.11f).coerceIn(8f, 11f).sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
      }
    }
  }
}

/**
 * 1. Compact Dashboard Card for LoveHomeScreen - Displays milestone progress and badge showcase preview
 */
@Composable
fun MilestoneBadgeDashboardCard(
  viewModel: InLoveViewModel,
  onOpenFullShowcase: () -> Unit,
  modifier: Modifier = Modifier
) {
  val loveDays by viewModel.loveDays.collectAsState()
  val badges by viewModel.loveBadges.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val boyName by viewModel.boyName.collectAsState()
  val girlName by viewModel.girlName.collectAsState()

  val unlockedBadges = badges.filter { it.targetDays <= loveDays }
  val nextLockedBadge = badges.firstOrNull { it.targetDays > loveDays }
  val totalBadges = badges.size.coerceAtLeast(1)
  val unlockedCount = unlockedBadges.size

  // Rank Calculation
  val rankTitle = when {
    unlockedCount >= 12 -> if (appLanguage == AppLanguage.VI) "Cặp Đôi Vũ Trụ Vĩnh Cửu 🌌" else "Cosmic Soulmates 🌌"
    unlockedCount >= 9 -> if (appLanguage == AppLanguage.VI) "Cặp Đôi Kim Cương Son Sắt 💎" else "Diamond Devotion 💎"
    unlockedCount >= 7 -> if (appLanguage == AppLanguage.VI) "Cặp Đôi Hồng Ngọc Chung Đôi 💍" else "Ruby Companions 💍"
    unlockedCount >= 5 -> if (appLanguage == AppLanguage.VI) "Cặp Đôi Hoàng Kim Vẹn Tròn 👑" else "Golden Heart Couple 👑"
    unlockedCount >= 3 -> if (appLanguage == AppLanguage.VI) "Cặp Đôi Bách Nhật Gắn Kết 💖" else "Silver Sweethearts 💖"
    else -> if (appLanguage == AppLanguage.VI) "Cặp Đôi Mầm Xanh Tình Yêu 🌱" else "Sprouting Romance 🌱"
  }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
    border = BorderStroke(1.2.dp, Color(0xFFFFC6DB)),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      // Top Title Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(listOf(Color(0xFFFF80AB), Color(0xFFFF4081)))
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.MilitaryTech,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = if (appLanguage == AppLanguage.VI) "HUY HIỆU & CỘT MỐC TÌNH YÊU" else "LOVE MILESTONES & BADGES",
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Primary,
              letterSpacing = 0.8.sp
            )
            Text(
              text = rankTitle,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF26071B)
            )
          }
        }

        // Badge Count Pill
        Surface(
          shape = RoundedCornerShape(50.dp),
          color = Color(0xFFFCE4EC),
          border = BorderStroke(1.dp, Color(0xFFFF80AB))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.EmojiEvents,
              contentDescription = null,
              tint = Color(0xFFFF4081),
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "$unlockedCount/$totalBadges",
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFFD81B60)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Master Progress Bar with Heart
      val overallProgress = (unlockedCount.toFloat() / totalBadges.toFloat()).coerceIn(0f, 1f)
      val animatedOverallProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "overall_progress"
      )

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = if (appLanguage == AppLanguage.VI) {
              "Đã mở khóa $unlockedCount huy hiệu trái tim"
            } else {
              "Unlocked $unlockedCount heart badges"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF555555)
          )
          Text(
            text = "${(overallProgress * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Color(0xFFF5E6EC))
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(animatedOverallProgress)
              .height(10.dp)
              .clip(RoundedCornerShape(50.dp))
              .background(
                Brush.horizontalGradient(
                  listOf(Color(0xFFFF80AB), Color(0xFFFF4081), Color(0xFFD81B60))
                )
              )
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Next Upcoming Milestone Countdown Teaser
      if (nextLockedBadge != null) {
        val daysLeft = nextLockedBadge.targetDays - loveDays
        val badgeTitle = if (appLanguage == AppLanguage.VI) nextLockedBadge.titleVi else nextLockedBadge.titleEn

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFFF7F9),
          border = BorderStroke(1.dp, Color(0xFFFFD5E5)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFFFE4EC)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.HourglassTop,
                  contentDescription = null,
                  tint = Color(0xFFFF4081),
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = if (appLanguage == AppLanguage.VI) "Mốc thưởng tiếp theo:" else "Next reward milestone:",
                  fontSize = 11.sp,
                  color = Color(0xFF777777),
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = "${nextLockedBadge.targetDays} Ngày • $badgeTitle",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF26071B),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(20.dp),
              color = Primary,
              shadowElevation = 2.dp
            ) {
              Text(
                text = if (appLanguage == AppLanguage.VI) "Còn $daysLeft ngày" else "${daysLeft}d left",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
      }

      // Horizontal Badges Quick Preview Strip
      Text(
        text = if (appLanguage == AppLanguage.VI) "Bộ Sưu Tập Huy Hiệu Nổi Bật" else "Featured Heart Badges",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF333333),
        modifier = Modifier.padding(bottom = 8.dp)
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        badges.take(7).forEach { badge ->
          val isUnlocked = badge.targetDays <= loveDays
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .clickable { viewModel.selectBadge(badge) }
              .padding(vertical = 4.dp)
          ) {
            HeartBadgeMedallion(
              badge = badge,
              isUnlocked = isUnlocked,
              size = 64.dp,
              showGlow = false,
              onClick = { viewModel.selectBadge(badge) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (appLanguage == AppLanguage.VI) badge.titleVi else badge.titleEn,
              fontSize = 10.sp,
              fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal,
              color = if (isUnlocked) Color(0xFF333333) else Color(0xFF888888),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.widthIn(max = 74.dp),
              textAlign = TextAlign.Center
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Button to open Full Interactive Milestone Badge Showcase
      Button(
        onClick = onOpenFullShowcase,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Primary
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("open_badge_tracker_button")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (appLanguage == AppLanguage.VI) {
              "Mở Bộ Sưu Tập Huy Hiệu & Lộ Trình Kỷ Niệm"
            } else {
              "Explore Full Milestone Badges & Roadmap"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

/**
 * 2. Full Milestone Badge Tracker Showcase Dialog/Modal
 */
@Composable
fun MilestoneBadgeShowcaseDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val loveDays by viewModel.loveDays.collectAsState()
  val badges by viewModel.loveBadges.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val boyName by viewModel.boyName.collectAsState()
  val girlName by viewModel.girlName.collectAsState()

  var selectedFilter by remember { mutableStateOf("ALL") } // ALL, UNLOCKED, LOCKED
  var viewMode by remember { mutableStateOf("TIMELINE") } // TIMELINE, GRID

  val unlockedCount = badges.count { it.targetDays <= loveDays }
  val totalCount = badges.size

  val filteredBadges = remember(badges, loveDays, selectedFilter) {
    when (selectedFilter) {
      "UNLOCKED" -> badges.filter { it.targetDays <= loveDays }
      "LOCKED" -> badges.filter { it.targetDays > loveDays }
      else -> badges
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFFFF7F9)),
      color = Color(0xFFFFF7F9)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // Top Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color(0xFF26071B)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = if (appLanguage == AppLanguage.VI) "Hành Trình Cột Mốc" else "Milestone Roadmap",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF26071B)
              )
              Text(
                text = if (appLanguage == AppLanguage.VI) "$boyName ❤️ $girlName • $loveDays Ngày Yêu" else "$boyName & $girlName • $loveDays Days",
                fontSize = 12.sp,
                color = Primary,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          // Toggle View Mode (Timeline vs Grid)
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(Color.White)
              .padding(3.dp)
          ) {
            IconButton(
              onClick = { viewMode = "TIMELINE" },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (viewMode == "TIMELINE") Primary else Color.Transparent)
            ) {
              Icon(
                imageVector = Icons.Filled.Timeline,
                contentDescription = "Timeline Mode",
                tint = if (viewMode == "TIMELINE") Color.White else Color(0xFF757575),
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(
              onClick = { viewMode = "GRID" },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (viewMode == "GRID") Primary else Color.Transparent)
            ) {
              Icon(
                imageVector = Icons.Filled.ViewModule,
                contentDescription = "Grid Mode",
                tint = if (viewMode == "GRID") Color.White else Color(0xFF757575),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Pills Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val filters = listOf(
            Triple("ALL", if (appLanguage == AppLanguage.VI) "Tất cả ($totalCount)" else "All ($totalCount)", Icons.Filled.AutoAwesome),
            Triple("UNLOCKED", if (appLanguage == AppLanguage.VI) "Đã nhận ($unlockedCount)" else "Unlocked ($unlockedCount)", Icons.Filled.CheckCircle),
            Triple("LOCKED", if (appLanguage == AppLanguage.VI) "Chờ mở (${totalCount - unlockedCount})" else "Upcoming (${totalCount - unlockedCount})", Icons.Filled.Lock)
          )

          filters.forEach { (key, label, icon) ->
            val isSelected = selectedFilter == key
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = if (isSelected) Primary else Color.White,
              border = BorderStroke(1.dp, if (isSelected) Primary else Color(0xFFFFD5E5)),
              shadowElevation = if (isSelected) 3.dp else 1.dp,
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { selectedFilter = key }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (isSelected) Color.White else Color(0xFF666666),
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = label,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else Color(0xFF444444)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content Area: Timeline or Grid
        if (viewMode == "TIMELINE") {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            items(filteredBadges) { badge ->
              val isUnlocked = badge.targetDays <= loveDays
              MilestoneTimelineCard(
                badge = badge,
                isUnlocked = isUnlocked,
                currentLoveDays = loveDays,
                appLanguage = appLanguage,
                onClick = { viewModel.selectBadge(badge) }
              )
            }
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            items(filteredBadges) { badge ->
              val isUnlocked = badge.targetDays <= loveDays
              MilestoneGridCard(
                badge = badge,
                isUnlocked = isUnlocked,
                currentLoveDays = loveDays,
                appLanguage = appLanguage,
                onClick = { viewModel.selectBadge(badge) }
              )
            }
          }
        }
      }
    }
  }
}

/**
 * 3. Timeline Item Card with Road Connector
 */
@Composable
fun MilestoneTimelineCard(
  badge: LoveBadgeEntity,
  isUnlocked: Boolean,
  currentLoveDays: Int,
  appLanguage: AppLanguage,
  onClick: () -> Unit
) {
  val theme = getBadgeTheme(badge.tier)
  val title = if (appLanguage == AppLanguage.VI) badge.titleVi else badge.titleEn
  val desc = if (appLanguage == AppLanguage.VI) badge.descVi else badge.descEn
  val progress = (currentLoveDays.toFloat() / badge.targetDays.toFloat()).coerceIn(0f, 1f)

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isUnlocked) Color.White else Color(0xFFFAF9FB)
    ),
    border = BorderStroke(
      1.2.dp,
      if (isUnlocked) theme.primaryColor.copy(alpha = 0.5f) else Color(0xFFE0E0E0)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 4.dp else 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left Badge Medallion
      HeartBadgeMedallion(
        badge = badge,
        isUnlocked = isUnlocked,
        size = 72.dp,
        showGlow = isUnlocked,
        onClick = onClick
      )

      Spacer(modifier = Modifier.width(14.dp))

      // Center Details
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "${badge.targetDays} " + if (appLanguage == AppLanguage.VI) "Ngày Yêu" else "Days",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isUnlocked) theme.primaryColor else Color(0xFF757575),
            letterSpacing = 0.5.sp
          )

          // Status Badge
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUnlocked) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
          ) {
            Text(
              text = if (isUnlocked) {
                if (appLanguage == AppLanguage.VI) "Đã Đạt Được ✨" else "Unlocked ✨"
              } else {
                val remaining = badge.targetDays - currentLoveDays
                if (appLanguage == AppLanguage.VI) "Còn $remaining ngày" else "${remaining}d left"
              },
              color = if (isUnlocked) Color(0xFF2E7D32) else Color(0xFFE65100),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
          text = title,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF26071B),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
          text = desc,
          fontSize = 12.sp,
          color = Color(0xFF666666),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Line
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(6.dp)
              .clip(RoundedCornerShape(50.dp))
              .background(Color(0xFFEAEAEA))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(
                  if (isUnlocked) {
                    Brush.horizontalGradient(theme.gradient)
                  } else {
                    Brush.horizontalGradient(listOf(Color(0xFFFFB74D), Color(0xFFFF9800)))
                  }
                )
            )
          }
          Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUnlocked) theme.primaryColor else Color(0xFF888888)
          )
        }
      }
    }
  }
}

/**
 * 4. Grid Item Card
 */
@Composable
fun MilestoneGridCard(
  badge: LoveBadgeEntity,
  isUnlocked: Boolean,
  currentLoveDays: Int,
  appLanguage: AppLanguage,
  onClick: () -> Unit
) {
  val theme = getBadgeTheme(badge.tier)
  val title = if (appLanguage == AppLanguage.VI) badge.titleVi else badge.titleEn

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isUnlocked) Color.White else Color(0xFFFAF9FB)
    ),
    border = BorderStroke(
      1.2.dp,
      if (isUnlocked) theme.primaryColor.copy(alpha = 0.4f) else Color(0xFFE0E0E0)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 4.dp else 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      HeartBadgeMedallion(
        badge = badge,
        isUnlocked = isUnlocked,
        size = 76.dp,
        showGlow = isUnlocked,
        onClick = onClick
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF26071B),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = "${badge.targetDays} " + if (appLanguage == AppLanguage.VI) "Ngày" else "Days",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (isUnlocked) theme.primaryColor else Color(0xFF888888)
      )

      Spacer(modifier = Modifier.height(6.dp))

      Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isUnlocked) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
      ) {
        Text(
          text = if (isUnlocked) {
            if (appLanguage == AppLanguage.VI) "Đã Đạt" else "Claimed"
          } else {
            val left = badge.targetDays - currentLoveDays
            if (appLanguage == AppLanguage.VI) "-$left ngày" else "${left}d"
          },
          color = if (isUnlocked) Color(0xFF2E7D32) else Color(0xFFE65100),
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }
  }
}

/**
 * 5. Interactive Digital Love Trophy & Reward Dialog
 */
@Composable
fun DigitalTrophyRewardDialog(
  badge: LoveBadgeEntity,
  currentLoveDays: Int,
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val appLanguage by viewModel.appLanguage.collectAsState()
  val boyName by viewModel.boyName.collectAsState()
  val girlName by viewModel.girlName.collectAsState()
  val anniversaryDate by viewModel.anniversaryDate.collectAsState()

  val isUnlocked = badge.targetDays <= currentLoveDays
  val theme = getBadgeTheme(badge.tier)
  val title = if (appLanguage == AppLanguage.VI) badge.titleVi else badge.titleEn
  val desc = if (appLanguage == AppLanguage.VI) badge.descVi else badge.descEn
  val quote = if (appLanguage == AppLanguage.VI) badge.rewardQuoteVi else badge.rewardQuoteEn

  var customNoteText by remember { mutableStateOf(badge.customNote) }
  var isEditingNote by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(32.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(16.dp)
        .clip(RoundedCornerShape(32.dp))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Top Close & Badge Tag
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = theme.secondaryColor,
            border = BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.5f))
          ) {
            Text(
              text = if (appLanguage == AppLanguage.VI) theme.tierNameVi else theme.tierNameEn,
              color = theme.primaryColor,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(Color(0xFFF5F5F5))
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close",
              tint = Color(0xFF666666)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Glowing Digital Badge
        HeartBadgeMedallion(
          badge = badge,
          isUnlocked = isUnlocked,
          size = 110.dp,
          showGlow = true,
          onClick = {
            if (isUnlocked) {
              viewModel.celebrateBadge(badge)
            }
          }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Trophy Title
        Text(
          text = title,
          fontSize = 22.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFF26071B),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = if (appLanguage == AppLanguage.VI) {
            "Cột mốc ${badge.targetDays} ngày yêu nhau"
          } else {
            "Milestone of ${badge.targetDays} days of love"
          },
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = theme.primaryColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Couple Honor Ribbon
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFFF0F5),
          border = BorderStroke(1.dp, Color(0xFFFFD1DC)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "$boyName & $girlName",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF26071B)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Romantic Quote / Vows Card
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9FA)),
          border = BorderStroke(1.dp, Color(0xFFFFE0EB)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (appLanguage == AppLanguage.VI) "Lời Yêu Thương Trao Nhau" else "Love Inscription",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "\"$quote\"",
              fontSize = 13.sp,
              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
              color = Color(0xFF424242),
              lineHeight = 19.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Description
        Text(
          text = desc,
          fontSize = 12.sp,
          color = Color(0xFF757575),
          textAlign = TextAlign.Center,
          lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Couple Memory Note Section
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFF9F9FB),
          border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (appLanguage == AppLanguage.VI) "Ghi Chú Kỷ Niệm Cột Mốc" else "Couple Milestone Note",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF555555)
              )
              IconButton(
                onClick = { isEditingNote = !isEditingNote },
                modifier = Modifier.size(48.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = "Edit note",
                  tint = Primary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            if (isEditingNote) {
              OutlinedTextField(
                value = customNoteText,
                onValueChange = { customNoteText = it },
                placeholder = {
                  Text(
                    text = if (appLanguage == AppLanguage.VI) "Viết cảm xúc của hai đứa..." else "Add your memory...",
                    fontSize = 12.sp
                  )
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Primary,
                  unfocusedBorderColor = Color(0xFFDDDDDD)
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                  viewModel.claimBadge(badge.id, customNoteText)
                  isEditingNote = false
                })
              )
              Spacer(modifier = Modifier.height(6.dp))
              Button(
                onClick = {
                  viewModel.claimBadge(badge.id, customNoteText)
                  isEditingNote = false
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.align(Alignment.End)
              ) {
                Text(
                  text = if (appLanguage == AppLanguage.VI) "Lưu ghi chú" else "Save Note",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            } else {
              Text(
                text = if (customNoteText.isNotEmpty()) {
                  customNoteText
                } else {
                  if (appLanguage == AppLanguage.VI) {
                    "Chưa có ghi chú. Bấm bút chì để viết kỷ niệm riêng của hai bạn!"
                  } else {
                    "No personal note yet. Tap pencil to record your memory!"
                  }
                },
                fontSize = 12.sp,
                color = if (customNoteText.isNotEmpty()) Color(0xFF333333) else Color(0xFF9E9E9E),
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons
        if (isUnlocked) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Re-celebrate button (Triggers floating hearts shower)
            Button(
              onClick = {
                viewModel.celebrateBadge(badge)
              },
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Primary),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("celebrate_badge_button")
            ) {
              Icon(
                imageVector = Icons.Filled.VolunteerActivism,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (appLanguage == AppLanguage.VI) "Bão Tim 🎉" else "Shower Hearts 🎉",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
              )
            }

            // Share Achievement Button
            OutlinedButton(
              onClick = {
                val shareText = "🏆 $boyName & $girlName đã đạt cột mốc \"$title\" (${badge.targetDays} ngày bên nhau)! 💕 $quote"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Love Milestone", shareText)
                clipboard.setPrimaryClip(clip)
                val msg = if (appLanguage == AppLanguage.VI) {
                  "Đã sao chép vinh danh cột mốc vào bộ nhớ tạm! 📋💕"
                } else {
                  "Milestone achievement copied to clipboard! 📋💕"
                }
                viewModel.showToast(msg)
              },
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.5.dp, Primary),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("share_badge_button")
            ) {
              Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (appLanguage == AppLanguage.VI) "Chia Sẻ" else "Share",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Primary
              )
            }
          }
        } else {
          // Locked Encouragement
          val daysToWait = badge.targetDays - currentLoveDays
          Button(
            onClick = {
              val msg = if (appLanguage == AppLanguage.VI) {
                "Cố lên nhé! Chỉ còn $daysToWait ngày nữa để hai bạn mở khóa huy hiệu này! 💕"
              } else {
                "Keep going! Only $daysToWait days left to unlock this heart badge! 💕"
              }
              viewModel.showToast(msg)
              viewModel.triggerFloatingHearts()
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (appLanguage == AppLanguage.VI) {
                "Đang Đếm Ngược: Còn $daysToWait Ngày"
              } else {
                "Countdown: $daysToWait Days Left"
              },
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = Color.White
            )
          }
        }
      }
    }
  }
}
