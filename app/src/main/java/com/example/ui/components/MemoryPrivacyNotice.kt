package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.DataResidency
import com.example.domain.DataResidencyPolicy
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary

/** Settings card: private memories stay on this phone. Hidden under cloud sync, where the copy would be false. */
@Composable
fun MemoryPrivacyNotice(
  modifier: Modifier = Modifier,
  residency: DataResidency = DataResidencyPolicy.current,
) {
  if (residency != DataResidency.LOCAL_ONLY) return
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier.fillMaxWidth().testTag("settings_privacy_notice")
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = stringResource(R.string.settings_privacy_local_title),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Primary
      )
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
