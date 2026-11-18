@file:Suppress("FunctionName")
package com.example.ui.screens.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.monetization.ProFeature
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Full InLove Pro subscription benefit matrix aligned with `:shared`'s [ProFeature].
 */
@Composable
fun ProBenefitsMatrixSection(
  headerText: String,
  isEnglish: Boolean,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    color = Color.White,
    border = BorderStroke(1.dp, InLoveColors.BorderSoftPink),
    modifier = modifier
      .fillMaxWidth()
      .testTag("paywall_pro_benefits_matrix")
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = headerText,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Primary
      )
      ProFeature.entries.forEach { feature ->
        ProBenefitRow(
          icon = featureIcon(feature),
          title = if (isEnglish) feature.titleEn else feature.titleVi,
          description = if (isEnglish) feature.descriptionEn else feature.descriptionVi
        )
      }
    }
  }
}

@Composable
private fun ProBenefitRow(
  icon: ImageVector,
  title: String,
  description: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = TextSubtlePlum,
      modifier = Modifier
        .padding(top = 2.dp)
        .size(16.dp)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = TextDarkPlum,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = description,
        color = TextSubtlePlum,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun featureIcon(feature: ProFeature): ImageVector = when (feature) {
  ProFeature.AD_FREE -> Icons.Filled.Block
  ProFeature.AI_GIFT_CONCIERGE_UNLIMITED -> Icons.Filled.CardGiftcard
  ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED -> Icons.Filled.Favorite
  ProFeature.EARLY_14D_30D_REMINDERS -> Icons.Filled.NotificationsActive
  ProFeature.MONTHIVERSARY_CADENCE -> Icons.Filled.Repeat
  ProFeature.PREMIUM_WALLPAPERS_AND_WIDGETS -> Icons.Filled.Widgets
}
