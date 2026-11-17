package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

// The Home partner card. The coupled and the offline card were the same layout drawn twice, so both use this one.
// The caller decides the age and the formatted zodiac; the card only draws, and hides a chip or row that has no value.
@Composable
internal fun PartnerCard(
  avatarUrl: String,
  name: String,
  age: Int,
  zodiac: String,
  birthDate: String,
  showHeartBadge: Boolean,
  avatarOuterSize: Dp,
  avatarInnerSize: Dp,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .clickable(onClick = onClick)
      .testTag(testTag)
  ) {
    Box(
      modifier = Modifier.size(avatarOuterSize),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .size(avatarInnerSize)
          .clip(CircleShape)
          .border(2.dp, Color(0xFFFF80AB), CircleShape)
          .shadow(3.dp, CircleShape)
      ) {
        ProfileAvatar(
          url = avatarUrl,
          contentDescription = name,
          modifier = Modifier.fillMaxSize()
        )
      }
      if (showHeartBadge) {
        // Mini floating heart badge
        Box(
          modifier = Modifier
            .size(20.dp)
            .align(Alignment.BottomEnd)
            .clip(CircleShape)
            .background(Color(0xFFFCE4EC))
            .border(1.5.dp, Color.White, CircleShape)
            .testTag("${testTag}_heart"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(11.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = name,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = TextDarkPlum,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    Spacer(modifier = Modifier.height(3.dp))

    // Badges: Soft Pastel Age & Zodiac
    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (age > 0) {
        Surface(
          shape = RoundedCornerShape(50.dp),
          color = Color(0xFFFCE4EC)
        ) {
          Text(
            text = "${age}t",
            color = Color(0xFFC2185B),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      if (zodiac.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(50.dp),
          color = Color(0xFFFFF0F5),
          modifier = Modifier.weight(1f, fill = false)
        ) {
          Text(
            text = zodiac,
            color = Color(0xFFAD1457),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }

    if (birthDate.isNotBlank()) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = birthDate,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = TextSubtlePlum
      )
    }
  }
}
