@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OnlineInviteEntity
import com.example.ui.theme.CardBorder
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
internal fun HomeStatusBanners(
  isOnline: Boolean,
  hasLocalOrOnlineName: Boolean,
  incomingInvite: OnlineInviteEntity?,
  showPairBanner: Boolean,
  isEnglish: Boolean,
  onOpenEditProfile: () -> Unit,
  onOpenPairingScreen: () -> Unit,
) {
  if (!isOnline) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color(0xFFECEFF1),
      border = BorderStroke(1.dp, Color(0xFFB0BEC5)),
      shadowElevation = 2.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
        .testTag("home_offline_banner")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFF455A64), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isEnglish) "You're offline" else "Bạn đang mất kết nối mạng",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF263238)
          )
          Text(
            text = if (isEnglish) "New memories and pairing changes will sync once you're back online."
            else "Kỷ niệm mới và thay đổi ghép đôi sẽ tự đồng bộ khi có mạng trở lại.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF455A64)
          )
        }
      }
    }
  }

  if (!hasLocalOrOnlineName) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color.White.copy(alpha = 0.96f),
      border = BorderStroke(1.dp, CardBorder),
      shadowElevation = 1.dp,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onOpenEditProfile)
        .padding(bottom = 8.dp)
        .testTag("home_anonymous_profile_banner")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Edit, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isEnglish) "Add your name and birthday" else "Thêm tên và ngày sinh của bạn",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = TextDarkPlum
          )
          Text(
            text = if (isEnglish) "Automatically calculates age and zodiac for your couple card"
            else "Giúp tính tự động tuổi và cung hoàng đạo cho thẻ hồ sơ đôi",
            fontSize = 11.5.sp,
            color = TextSubtlePlum
          )
        }
      }
    }
  }

  if (incomingInvite != null) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color(0xFFFFF0F5),
      border = BorderStroke(1.dp, CardBorder),
      shadowElevation = 2.dp,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onOpenPairingScreen)
        .padding(bottom = 8.dp)
        .testTag("home_incoming_invite_banner")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isEnglish) "Pairing invite from ${incomingInvite.effectiveSenderName.ifBlank { "Partner" }}"
            else "Lời mời ghép đôi từ ${incomingInvite.effectiveSenderName.ifBlank { "Người ấy" }}",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = TextDarkPlum
          )
          Text(
            text = if (isEnglish) "Review partner details and start date before accepting"
            else "Kiểm tra thông tin và ngày bắt đầu trước khi đồng ý",
            fontSize = 11.5.sp,
            color = TextSubtlePlum
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = Primary) {
          Text(
            text = if (isEnglish) "View" else "Xem",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }
    }
  } else if (showPairBanner) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color.White.copy(alpha = 0.95f),
      border = BorderStroke(1.dp, Color(0xFFFFC6DB)),
      shadowElevation = 2.dp,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onOpenPairingScreen)
        .padding(bottom = 8.dp)
        .testTag("home_prompt_pair_banner")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isEnglish) "Pair with your partner" else "Ghép đôi cùng người ấy",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Text(
          text = if (isEnglish) "Pair >" else "Ghép >",
          fontSize = 11.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Primary
        )
      }
    }
  }
}
