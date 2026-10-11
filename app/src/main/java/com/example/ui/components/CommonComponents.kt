@file:Suppress("FunctionName")
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

const val COUPLE_AVATAR_URL =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuA7FGhAVlT3aua6tzpkBgh-_XXVsY-hCGooIPxOJ0acYyvZV9FZpASsMTgGO0vBFJLrLojRgwwnQEjfWAniwj2FAiKNpUjSRFNIQgmOhVN8fFSYwpOqLF8hAnWk3UM0dWxSVT1FwaK9ovXMP7Jwi-gjfRbUm2ISX1qyUY6bpBKBZnBYk7YIorizTPWI5c6w9XUdTTsMLFWT3c5ns8lYQooC0eEvt6S5zJNNobW_pn1PTJOb0K2REgdfTQ"

@Composable
fun InLoveTopBar(
  title: String,
  hasUnread: Boolean = false,
  onBellClick: () -> Unit = {},
  onProfileClick: () -> Unit = {},
  includeStatusBarPadding: Boolean = true
) {
  val barShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(elevation = 8.dp, shape = barShape, ambientColor = Primary.copy(alpha = 0.25f), spotColor = Primary.copy(alpha = 0.25f))
      .clip(barShape)
      .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF3F7))))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (includeStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
        .padding(horizontal = 16.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = title,
          color = OnSurface,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        NotificationBellButton(hasUnread = hasUnread, onClick = onBellClick)
        ProfileAvatarButton(onClick = onProfileClick)
      }
    }
  }
}

/** Glass bell with an unread dot; replaces the old heart button. */
@Composable
fun NotificationBellButton(hasUnread: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(36.dp)
      .clip(CircleShape)
      .background(PrimaryFixed.copy(alpha = 0.55f))
      .clickable { onClick() }
      .testTag("topbar_bell_button")
  ) {
    Icon(
      imageVector = if (hasUnread) Icons.Filled.Notifications else Icons.Outlined.Notifications,
      contentDescription = "Notifications",
      tint = Primary,
      modifier = Modifier.size(20.dp)
    )
    AnimatedVisibility(visible = hasUnread, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(Primary).border(1.5.dp, Color.White, CircleShape))
    }
  }
}

/** Single profile entry point, shared by the top bar and Home so the top-right corner is identical on every tab. */
@Composable
fun ProfileAvatarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(36.dp)
      .shadow(4.dp, CircleShape)
      .clip(CircleShape)
      .border(1.5.dp, Primary.copy(alpha = 0.6f), CircleShape)
      .clickable { onClick() }
      .testTag("topbar_profile_avatar")
  ) {
    AsyncImage(
      model = COUPLE_AVATAR_URL,
      contentDescription = "Profile",
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(36.dp)
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
      .padding(horizontal = 10.dp, vertical = 4.dp),
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
              .clickable { onTabSelected(index) }
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
      .clickable { onClick() }
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
