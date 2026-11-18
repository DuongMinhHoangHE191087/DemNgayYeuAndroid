package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.DataResidency
import com.example.domain.DataResidencyPolicy
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary

/** Settings card: private memories stay on this phone. Collapsible on Settings; hidden under cloud sync. */
@Composable
fun MemoryPrivacyNotice(
  modifier: Modifier = Modifier,
  residency: DataResidency = DataResidencyPolicy.current,
  initiallyExpanded: Boolean = true,
) {
  if (residency != DataResidency.LOCAL_ONLY) return
  var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
  val isEnglish = androidx.core.os.ConfigurationCompat.getLocales(
    androidx.compose.ui.platform.LocalConfiguration.current
  )[0]?.language == "en"

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier.fillMaxWidth().testTag("settings_privacy_notice")
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox)
          .clip(RoundedCornerShape(10.dp))
          .clickable { expanded = !expanded }
          .testTag("btn_toggle_privacy_details"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.settings_privacy_local_title),
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = Primary,
          modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
          text = when {
            expanded && isEnglish -> "Hide"
            expanded -> "Thu gọn"
            isEnglish -> "Show details"
            else -> "Xem chi tiết"
          },
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = Primary,
        )
      }
      if (!expanded) {
        Text(
          text = if (isEnglish) {
            "Photos and private notes remain on this device."
          } else {
            "Ảnh và ghi chú kỷ niệm chỉ lưu trên thiết bị này."
          },
          fontSize = 12.sp,
          color = OnSurfaceVariant,
        )
      }
      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = stringResource(R.string.settings_privacy_local_body),
            fontSize = 12.sp,
            color = OnSurfaceVariant
          )
          Text(
            text = stringResource(R.string.settings_privacy_local_warning),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}
