@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.ai.AdviceSource
import com.example.shared.ai.AiGiftAdviceBundle
import com.example.shared.ai.LoveNoteDraft
import com.example.shared.ai.RelationshipTone
import com.example.shared.ai.RichGiftRecommendation
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveChoiceChip
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.InLoveSectionHeader
import com.example.ui.designsystem.InLoveStatusPill
import com.example.ui.designsystem.InLoveTintedCard
import com.example.ui.theme.CardBorder
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiGiftConciergeSection(
  selectedTone: RelationshipTone,
  occasionTitle: String,
  adviceBundle: AiGiftAdviceBundle?,
  isLoading: Boolean,
  isVip: Boolean,
  remainingAllowance: Int,
  softPaywallPrompt: SoftPaywallPrompt?,
  isEnglish: Boolean,
  onSelectTone: (RelationshipTone) -> Unit,
  onGenerateAdvice: () -> Unit,
  onOpenPaywall: () -> Unit,
  onCopyLoveNote: (String) -> Unit,
  onShareText: (String) -> Unit,
  onSaveGiftReminder: (RichGiftRecommendation) -> Unit,
  modifier: Modifier = Modifier,
) {
  val quotaBadge = when {
    isVip -> "Pro · $remainingAllowance/${ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH}"
    remainingAllowance > 0 -> if (isEnglish) "$remainingAllowance free left" else "Còn $remainingAllowance lượt miễn phí"
    else -> if (isEnglish) "Free quota used" else "Đã dùng hết lượt miễn phí"
  }

  InLoveTintedCard(
    containerColor = InLoveColors.SoftBlushCard,
    borderColor = InLoveColors.BorderSoftPink,
    modifier = modifier.testTag("ai_gift_concierge_section")
  ) {
    InLoveSectionHeader(
      title = if (isEnglish) "Gift & Love-Note Concierge" else "Trợ Lý Quà Tặng & Lời Chúc",
      subtitle = occasionTitle.takeIf { it.isNotBlank() }?.let {
        if (isEnglish) "Upcoming: $it" else "Dịp sắp tới: $it"
      },
      trailing = {
        InLoveStatusPill(
          text = quotaBadge,
          containerColor = if (isVip) InLoveColors.SuccessBg else InLoveColors.RoseTint,
          contentColor = if (isVip) InLoveColors.SuccessText else InLoveColors.RoseHighlight,
          borderColor = if (isVip) InLoveColors.SuccessBorder else CardBorder
        )
      }
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      RelationshipTone.entries.forEach { tone ->
        InLoveChoiceChip(
          label = if (isEnglish) tone.titleEn else tone.titleVi,
          selected = selectedTone == tone,
          onClick = { onSelectTone(tone) },
          testTag = "tone_chip_${tone.name}"
        )
      }
    }

    PrimaryGradientButton(
      text = if (isLoading) {
        if (isEnglish) "Preparing ideas..." else "Đang soạn gợi ý..."
      } else {
        if (isEnglish) "Suggest Gifts & Love Notes" else "Gợi Ý Quà & Soạn Lời Chúc"
      },
      onClick = { if (!isLoading) onGenerateAdvice() },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox),
      testTag = "btn_generate_ai_advice"
    )

    if (isLoading) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
      }
    }

    if (softPaywallPrompt != null && !isVip) {
      Surface(
        shape = RoundedCornerShape(InLoveRadii.InputSoft),
        color = InLoveColors.WarningBg,
        border = BorderStroke(1.dp, InLoveColors.WarningBorder),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ai_concierge_soft_paywall")
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = if (isEnglish) softPaywallPrompt.titleEn else softPaywallPrompt.titleVi,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = InLoveColors.WarningText
          )
          Text(
            text = if (isEnglish) softPaywallPrompt.messageEn else softPaywallPrompt.messageVi,
            fontSize = 12.sp,
            color = TextDarkPlum,
            lineHeight = 17.sp
          )
          SecondaryButton(
            text = if (isEnglish) softPaywallPrompt.ctaEn else softPaywallPrompt.ctaVi,
            onClick = onOpenPaywall,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = MinTouchBox),
            testTag = "btn_ai_concierge_upgrade"
          )
        }
      }
    }

    if (adviceBundle != null) {
      AdviceBundleResultView(
        bundle = adviceBundle,
        isEnglish = isEnglish,
        onCopyLoveNote = onCopyLoveNote,
        onShareText = onShareText,
        onSaveGiftReminder = onSaveGiftReminder
      )
    }
  }
}

@Composable
private fun AdviceBundleResultView(
  bundle: AiGiftAdviceBundle,
  isEnglish: Boolean,
  onCopyLoveNote: (String) -> Unit,
  onShareText: (String) -> Unit,
  onSaveGiftReminder: (RichGiftRecommendation) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = bundle.summaryHeadline,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextDarkPlum,
        modifier = Modifier.weight(1f)
      )
      InLoveStatusPill(
        text = if (bundle.source == AdviceSource.CLOUD_AI) {
          if (isEnglish) "Tailored" else "Riêng hai bạn"
        } else {
          if (isEnglish) "Curated" else "Gợi ý chọn lọc"
        }
      )
    }

    if (bundle.tabooWarnings.isNotEmpty()) {
      Surface(
        shape = RoundedCornerShape(InLoveRadii.Input),
        color = InLoveColors.WarningBg,
        border = BorderStroke(1.dp, InLoveColors.WarningBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = if (isEnglish) "Allergy & preference notes" else "Lưu ý dị ứng & kiêng kỵ",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = InLoveColors.WarningText
          )
          Text(
            text = bundle.tabooWarnings.joinToString("\n"),
            fontSize = 12.sp,
            color = TextDarkPlum,
            lineHeight = 17.sp
          )
        }
      }
    }

    if (bundle.prepTimeline.isNotEmpty()) {
      Surface(
        shape = RoundedCornerShape(InLoveRadii.Input),
        color = Color.White,
        border = BorderStroke(1.dp, InLoveColors.BorderRose),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = if (isEnglish) "Prep timeline" else "Lịch chuẩn bị đề xuất",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary
          )
          bundle.prepTimeline.forEach { step ->
            Text(text = "• $step", fontSize = 12.sp, color = TextSubtlePlum, lineHeight = 17.sp)
          }
        }
      }
    }

    bundle.recommendations.forEach { rec ->
      ConciergeRecommendationCard(
        rec = rec,
        isEnglish = isEnglish,
        onShare = { onShareText("${rec.title}\n${rec.priceRangeLabel}\n${rec.reason}") },
        onSaveReminder = { onSaveGiftReminder(rec) }
      )
    }

    if (bundle.loveNotes.isNotEmpty()) {
      Text(
        text = if (isEnglish) "Ready-to-send love notes" else "Gợi ý lời chúc & tin nhắn",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextDarkPlum
      )
      bundle.loveNotes.forEach { draft ->
        LoveNoteDraftCard(
          draft = draft,
          isEnglish = isEnglish,
          onCopy = { onCopyLoveNote(draft.message) },
          onShare = { onShareText(draft.message) }
        )
      }
    }
  }
}

@Composable
private fun ConciergeRecommendationCard(
  rec: RichGiftRecommendation,
  isEnglish: Boolean,
  onShare: () -> Unit,
  onSaveReminder: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color.White,
    border = BorderStroke(1.dp, CardBorder),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = rec.title,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDarkPlum,
          modifier = Modifier.weight(1f)
        )
        Text(
          text = rec.priceRangeLabel,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = Primary
        )
      }
      Text(text = rec.reason, fontSize = 12.sp, color = TextDarkPlum, lineHeight = 17.sp)
      Text(text = rec.preparationPlan, fontSize = 12.sp, color = TextSubtlePlum, lineHeight = 17.sp)
      if (rec.personalizationHint.isNotBlank()) {
        Text(
          text = if (isEnglish) "Tailored touch: ${rec.personalizationHint}" else "Gợi ý riêng: ${rec.personalizationHint}",
          fontSize = 12.sp,
          color = InLoveColors.RoseHighlight
        )
      }
      if (!rec.culturalCaution.isNullOrBlank()) {
        Text(text = rec.culturalCaution.orEmpty(), fontSize = 11.5.sp, color = InLoveColors.WarningText)
      }
      Spacer(modifier = Modifier.height(4.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onSaveReminder, modifier = Modifier.size(MinTouchBox)) {
          Icon(
            imageVector = Icons.Filled.AlarmAdd,
            contentDescription = if (isEnglish) "Save reminder" else "Lưu nhắc quà",
            tint = Primary
          )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(MinTouchBox)) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = if (isEnglish) "Share gift" else "Chia sẻ quà",
            tint = InLoveColors.RoseHighlight
          )
        }
      }
    }
  }
}

@Composable
private fun LoveNoteDraftCard(
  draft: LoveNoteDraft,
  isEnglish: Boolean,
  onCopy: () -> Unit,
  onShare: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color.White,
    border = BorderStroke(1.dp, InLoveColors.LavenderBorder),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text = draft.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Primary)
      Text(text = draft.message, fontSize = 12.5.sp, color = TextDarkPlum, lineHeight = 18.sp)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onCopy, modifier = Modifier.size(MinTouchBox)) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = if (isEnglish) "Copy note" else "Sao chép lời chúc",
            tint = Primary
          )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(MinTouchBox)) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = if (isEnglish) "Share note" else "Chia sẻ lời chúc",
            tint = InLoveColors.RoseHighlight
          )
        }
      }
    }
  }
}
