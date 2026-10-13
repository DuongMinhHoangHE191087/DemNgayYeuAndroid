@file:Suppress("FunctionName")
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import com.example.ui.theme.MinTouchBox
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.CardBorder
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.SurfaceContainerHighest

import androidx.compose.ui.text.style.TextOverflow

const val COUPLE_AVATAR_URL =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuA7FGhAVlT3aua6tzpkBgh-_XXVsY-hCGooIPxOJ0acYyvZV9FZpASsMTgGO0vBFJLrLojRgwwnQEjfWAniwj2FAiKNpUjSRFNIQgmOhVN8fFSYwpOqLF8hAnWk3UM0dWxSVT1FwaK9ovXMP7Jwi-gjfRbUm2ISX1qyUY6bpBKBZnBYk7YIorizTPWI5c6w9XUdTTsMLFWT3c5ns8lYQooC0eEvt6S5zJNNobW_pn1PTJOb0K2REgdfTQ"

/**
 * Clean & romantic top bar: Title, bell notification, and profile avatar are strictly aligned
 * on a single horizontal row, with optional collapsing notification strip when active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InLoveTopBar(
  title: String,
  avatarUrl: String,
  latest: NotificationItem?,
  isEnglish: Boolean,
  scrollBehavior: TopAppBarScrollBehavior,
  hasUnread: Boolean = false,
  onBellClick: () -> Unit = {},
  onProfileClick: () -> Unit = {},
  onSeeAll: () -> Unit = {},
  includeStatusBarPadding: Boolean = true
) {
  val state = scrollBehavior.state
  val barShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(elevation = 6.dp, shape = barShape, ambientColor = Primary.copy(alpha = 0.18f), spotColor = Primary.copy(alpha = 0.18f))
      .clip(barShape)
      .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF6F9))))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (includeStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = title,
            color = Color(0xFF2B0A1E),
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("topbar_large_title")
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          NotificationBellButton(
            hasUnread = hasUnread,
            contentDescription = if (isEnglish) "Notifications" else "Thông báo",
            onClick = onBellClick
          )
          ProfileAvatarButton(
            avatarUrl = avatarUrl,
            contentDescription = if (isEnglish) "Your profile" else "Hồ sơ của bạn",
            onClick = onProfileClick
          )
        }
      }

      if (latest != null) {
        Column(
          modifier = Modifier
            .clipToBounds()
            .layout { m, c ->
              val p = m.measure(c.copy(minHeight = 0))
              if (state.heightOffsetLimit != -p.height.toFloat()) state.heightOffsetLimit = -p.height.toFloat()
              val h = (p.height + state.heightOffset).roundToInt().coerceAtLeast(0)
              layout(p.width, h) { p.place(0, h - p.height) }
            }
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
        ) {
          NotificationStrip(latest, isEnglish, onSeeAll)
        }
      } else {
        if (state.heightOffsetLimit == 0f) {
          state.heightOffsetLimit = -1f
        }
      }
    }
  }
}

@Composable
private fun NotificationStrip(item: NotificationItem, isEnglish: Boolean, onSeeAll: () -> Unit) {
  val stripSource = remember { MutableInteractionSource() }
  val seeAllSource = remember { MutableInteractionSource() }
  Surface(
    onClick = onSeeAll,
    interactionSource = stripSource,
    shape = MaterialTheme.shapes.large,
    color = PrimaryFixed,
    modifier = Modifier.fillMaxWidth().pressScale(stripSource).testTag("topbar_notification_strip")
  ) {
    Row(
      modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(item.emoji, fontSize = 22.sp)
      Column(Modifier.weight(1f)) {
        Text(item.title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
          item.whenText,
          color = if (item.urgent) Primary else OnSurfaceVariant,
          fontSize = 12.sp
        )
      }
      TextButton(
        onClick = onSeeAll,
        interactionSource = seeAllSource,
        modifier = Modifier.pressScale(seeAllSource).testTag("topbar_see_all")
      ) {
        Text(if (isEnglish) "See all" else "Xem tất cả", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

/** Glass bell with an unread dot; the tappable box is [MinTouchBox] so it renders >= 48dp. */
@Composable
fun NotificationBellButton(
  hasUnread: Boolean,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(MinTouchBox)
      .clip(CircleShape)
      .pressClickable(onClick = onClick)
      .testTag("topbar_bell_button")
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(36.dp).clip(CircleShape).background(PrimaryFixed.copy(alpha = 0.55f))
    ) {
      Icon(
        imageVector = if (hasUnread) Icons.Filled.Notifications else Icons.Outlined.Notifications,
        contentDescription = contentDescription,
        tint = Primary,
        modifier = Modifier.size(20.dp)
      )
      AnimatedVisibility(visible = hasUnread, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Primary).border(1.5.dp, Color.White, CircleShape))
      }
    }
  }
}

/** Single profile entry point of the top bar; a blank [avatarUrl] shows the default couple image. */
@Composable
fun ProfileAvatarButton(
  avatarUrl: String,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(MinTouchBox)
      .clip(CircleShape)
      .pressClickable(onClick = onClick)
      .testTag("topbar_profile_avatar")
  ) {
    AsyncImage(
      model = avatarUrl.ifBlank { COUPLE_AVATAR_URL },
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .size(36.dp)
        .shadow(4.dp, CircleShape)
        .clip(CircleShape)
        .border(1.5.dp, Primary.copy(alpha = 0.6f), CircleShape)
    )
  }
}

data class NavTabItem(
  val title: String,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
  val tag: String
)

@Composable
fun InLoveBottomNav(
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
  labels: List<String>? = null
) {
  val defaultTitles = listOf("Trang Chủ", "Kỷ Niệm", "Lịch", "Gợi Ý Quà", "Cài Đặt")
  val titles = labels ?: defaultTitles
  val tabs = listOf(
    NavTabItem(titles.getOrElse(0) { "Trang Chủ" }, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "nav_tab_love"),
    NavTabItem(titles.getOrElse(1) { "Kỷ Niệm" }, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_tab_memories"),
    NavTabItem(titles.getOrElse(2) { "Lịch" }, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_tab_calendar"),
    NavTabItem(titles.getOrElse(3) { "Gợi Ý Quà" }, Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard, "nav_tab_gifts"),
    NavTabItem(titles.getOrElse(4) { "Cài Đặt" }, Icons.Filled.Tune, Icons.Outlined.Tune, "nav_tab_settings")
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 8.dp,
      border = BorderStroke(1.2.dp, CardBorder),
      modifier = Modifier
        .fillMaxWidth()
        .height(58.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Icon-only tabs; the selected tab grows into a pill that reveals its label.
        tabs.forEachIndexed { index, tab ->
          val isSelected = selectedTab == index
          val icon = if (isSelected) tab.activeIcon else tab.inactiveIcon
          val spring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
          val weight by animateFloatAsState(if (isSelected) 2f else 1f, spring, label = "nav_weight")
          val iconScale by animateFloatAsState(if (isSelected) 1.12f else 1f, spring, label = "nav_icon_scale")
          val pill by animateColorAsState(
            if (isSelected) PrimaryFixed.copy(alpha = 0.75f) else Color.Transparent,
            label = "nav_pill"
          )
          val tint by animateColorAsState(if (isSelected) Primary else OnSurfaceVariant, label = "nav_tint")

          Box(
            modifier = Modifier
              .weight(weight)
              .padding(horizontal = 3.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(pill)
              .pressClickable(onClick = { onTabSelected(index) })
              .padding(vertical = 9.dp)
              .testTag(tab.tag),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = icon,
                contentDescription = tab.title,
                tint = tint,
                modifier = Modifier
                  .size(22.dp)
                  .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
              )
              AnimatedVisibility(visible = isSelected) {
                Text(
                  text = tab.title,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Primary,
                  maxLines = 1,
                  modifier = Modifier.padding(start = 4.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun PrimaryGradientButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  testTag: String = "primary_gradient_btn"
) {
  Box(
    modifier = modifier
      .shadow(8.dp, RoundedCornerShape(50.dp), spotColor = Primary.copy(alpha = 0.4f))
      .clip(RoundedCornerShape(50.dp))
      .background(
        Brush.linearGradient(
          listOf(RoseGradientStart, RoseGradientMid, RoseGradientEnd)
        )
      )
      .pressClickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 13.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
      }
      Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
      )
    }
  }
}

/**
 * Modifier extension to give popup dialogs a smooth, romantic spring-entrance animation.
 * Softly scales from 0.92f to 1.0f with bouncy spring easing, floats up from 28dp with
 * spring physics, and fades alpha from 0 to 1 for a unified, modern iOS/Material 3 feel.
 */
@Composable
fun Modifier.animateDialogEntrance(): Modifier {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    visible = true
  }
  val scale by animateFloatAsState(
    targetValue = if (visible) 1f else 0.92f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    ),
    label = "dialog_entrance_scale"
  )
  val translateY by animateFloatAsState(
    targetValue = if (visible) 0f else 28f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    ),
    label = "dialog_entrance_translate_y"
  )
  val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(
      durationMillis = 220,
      easing = FastOutSlowInEasing
    ),
    label = "dialog_entrance_alpha"
  )
  return this.graphicsLayer {
    scaleX = scale
    scaleY = scale
    translationY = translateY
    this.alpha = alpha
  }
}

/**
 * Romantic reload & data syncing badge with rotating heart and gentle glowing pulse.
 */
@Composable
fun RomanticSyncingBadge(
  text: String,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "sync_badge_pulse")
  val rotateAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "sync_badge_rotation"
  )
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "sync_badge_alpha"
  )

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color.White.copy(alpha = 0.92f),
    border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f)),
    shadowElevation = 4.dp,
    modifier = modifier.graphicsLayer { alpha = pulseAlpha }
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = Primary,
        modifier = Modifier
          .size(16.dp)
          .graphicsLayer { rotationZ = rotateAngle }
      )
      Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Primary
      )
    }
  }
}

