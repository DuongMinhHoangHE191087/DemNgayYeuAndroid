@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.completenessPercent
import com.example.shared.dossier.hasClothingOrRingSizes
import com.example.shared.monetization.ProTierCatalog
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.InLoveStatusPill
import com.example.ui.designsystem.InLoveSurfaceCard
import com.example.ui.designsystem.InLoveTypography
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartnerDossierCard(
  dossier: PartnerDossier,
  isVip: Boolean,
  isEnglish: Boolean,
  onOpenEditor: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val completeness = dossier.completenessPercent()
  val notesCapLabel = if (isVip) {
    if (isEnglish) "${dossier.secretSurpriseNotes.size} secret notes" else "${dossier.secretSurpriseNotes.size} ghi chú bí mật"
  } else {
    "${dossier.secretSurpriseNotes.size}/${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} " +
      if (isEnglish) "secret notes" else "ghi chú bí mật"
  }
  val titleText = dossier.nickname.takeIf { it.isNotBlank() }?.let {
    if (isEnglish) "Dossier · $it" else "Hồ sơ của $it"
  } ?: if (isEnglish) "Partner Dossier" else "Hồ Sơ Người Thương"

  InLoveSurfaceCard(
    modifier = modifier.testTag("partner_dossier_card")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = titleText,
        style = InLoveTypography.SectionHeader,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "$completeness%",
        style = InLoveTypography.TabularMetric,
      )
    }

    LinearProgressIndicator(
      progress = { (completeness / 100f).coerceIn(0f, 1f) },
      modifier = Modifier
        .fillMaxWidth()
        .height(7.dp)
        .clip(RoundedCornerShape(InLoveRadii.Pill)),
      color = Primary,
      trackColor = InLoveColors.RoseTintTrack,
      strokeCap = StrokeCap.Round,
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (dossier.loveLanguages.isNotEmpty()) {
        val firstLang = dossier.loveLanguages.first()
        InLoveStatusPill(
          text = if (isEnglish) firstLang.titleEn else firstLang.titleVi
        )
      }
      if (dossier.interestCategories.isNotEmpty()) {
        InLoveStatusPill(
          text = if (isEnglish) "${dossier.interestCategories.size} interests" else "${dossier.interestCategories.size} sở thích"
        )
      }
      if (dossier.hasClothingOrRingSizes()) {
        InLoveStatusPill(
          text = if (isEnglish) "Sizes saved" else "Đã lưu size"
        )
      }
      if (dossier.foodAndCare.allergies.isNotEmpty() || dossier.foodAndCare.dislikesOrTaboos.isNotEmpty()) {
        InLoveStatusPill(
          text = if (isEnglish) "Dietary filters active" else "Đã bật lọc dị ứng/kiêng",
          containerColor = InLoveColors.WarningBg,
          contentColor = InLoveColors.WarningText,
          borderColor = InLoveColors.WarningBorder
        )
      }
      InLoveStatusPill(
        text = notesCapLabel,
        containerColor = InLoveColors.LavenderCardBg,
        contentColor = InLoveColors.TextPrimary,
        borderColor = InLoveColors.LavenderBorder
      )
    }

    PrimaryGradientButton(
      text = if (isEnglish) "Edit dossier" else "Cập nhật hồ sơ",
      onClick = onOpenEditor,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox),
      testTag = "btn_open_partner_dossier"
    )
  }
}
