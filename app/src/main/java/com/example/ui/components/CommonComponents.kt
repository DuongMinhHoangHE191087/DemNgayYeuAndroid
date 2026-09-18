@file:Suppress("FunctionName")
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
  onHeartClick: () -> Unit = {},
  onProfileClick: () -> Unit = {}
) {
  Surface(
    color = Color.White.copy(alpha = 0.98f),
    modifier = Modifier
      .fillMaxWidth()
      .shadow(elevation = 3.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left brand & screen title
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // App logo icon badge
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              Brush.linearGradient(
                listOf(RoseGradientStart, RoseGradientMid)
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "InLove Logo",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }

        Column {
          Text(
            text = "INLOVE",
            color = Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )
          Text(
            text = title,
            color = OnSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Right actions: heart button + couple profile avatar
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        IconButton(
          onClick = onHeartClick,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PrimaryFixed.copy(alpha = 0.5f))
            .testTag("topbar_heart_button")
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Chia sẻ kỷ niệm",
            tint = Primary,
            modifier = Modifier.size(20.dp)
          )
        }

        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .shadow(4.dp, CircleShape)
            .clickable { onProfileClick() }
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
    }
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
        tabs.forEachIndexed { index, tab ->
          val isSelected = selectedTab == index
          val icon = if (isSelected) tab.activeIcon else tab.inactiveIcon

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(20.dp))
              .clickable { onTabSelected(index) }
              .padding(vertical = 4.dp)
              .testTag(tab.tag),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    if (isSelected) PrimaryFixed.copy(alpha = 0.7f) else Color.Transparent
                  )
                  .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = tab.title,
                  tint = if (isSelected) Primary else OnSurfaceVariant,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.height(1.dp))
              Text(
                text = tab.title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) Primary else OnSurfaceVariant,
                maxLines = 1
              )
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
