@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.calendar.NextOccasionReadiness
import com.example.shared.calendar.PreparationStage
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.InLoveStatusPill
import com.example.ui.theme.CardBorder
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Displays the upcoming occasion title, date countdown, stage badge, and quick navigation.
 */
@Composable
fun NextOccasionReadinessCard(
  readiness: NextOccasionReadiness,
  isSacredCelebrationToday: Boolean,
  isEnglish: Boolean,
  onOpenGifts: () -> Unit,
  onOpenCalendar: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isCelebration = isSacredCelebrationToday || readiness.stage == PreparationStage.TODAY_CELEBRATION
  val title = if (isEnglish) readiness.titleEn else readiness.titleVi
  val celebrationNote = if (isEnglish) readiness.actionSummaryEn else readiness.actionSummaryVi
  val formattedDate = formatReadinessDate(readiness.targetDateIso, isEnglish)
  val isUrgent = readiness.stage == PreparationStage.URGENT_LAST_MINUTE || readiness.stage == PreparationStage.ACTION_NEEDED

  val stageLabel = when (readiness.stage) {
    PreparationStage.TODAY_CELEBRATION -> if (isEnglish) "Sacred Day · Ad-Free" else "Ngày Kỷ Niệm · Không QC"
    PreparationStage.URGENT_LAST_MINUTE -> if (isEnglish) "Act Today" else "Cần chuẩn bị gấp"
    PreparationStage.ACTION_NEEDED -> if (isEnglish) "Prep Window" else "Nên chuẩn bị quà"
    PreparationStage.PLAN_AHEAD_BOOKING -> if (isEnglish) "Book Early" else "Nên đặt sớm"
    PreparationStage.RELAXED -> if (isEnglish) "Upcoming" else "Sắp tới"
  }

  val pillBg = when {
    isCelebration -> Primary
    isUrgent -> InLoveColors.WarningBg
    else -> InLoveColors.RoseTint
  }
  val pillFg = when {
    isCelebration -> Color.White
    isUrgent -> InLoveColors.WarningText
    else -> InLoveColors.RoseHighlight
  }
  val pillBorder = when {
    isCelebration -> Primary
    isUrgent -> InLoveColors.WarningBorder
    else -> CardBorder
  }

  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(
      containerColor = if (isCelebration) InLoveColors.SoftBlushCard else Color.White.copy(alpha = 0.98f)
    ),
    border = BorderStroke(1.dp, if (isCelebration) Primary else CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_next_occasion_readiness_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            fontFamily = InLoveFonts.DisplaySerif,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDarkPlum,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(3.dp))
          val metaText = if (readiness.daysRemaining == 0) {
            if (isEnglish) "Today · $formattedDate" else "Hôm nay · $formattedDate"
          } else {
            if (isEnglish) "$formattedDate · In ${readiness.daysRemaining}d" else "$formattedDate · Còn ${readiness.daysRemaining} ngày"
          }
          Text(
            text = metaText,
            style = TextStyle(
              fontFamily = InLoveFonts.BodySans,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = TextSubtlePlum,
              fontFeatureSettings = "tnum",
            )
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        InLoveStatusPill(
          text = stageLabel,
          containerColor = pillBg,
          contentColor = pillFg,
          borderColor = pillBorder
        )
      }

      if (isCelebration && celebrationNote.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = celebrationNote,
          fontSize = 13.sp,
          color = TextDarkPlum,
          lineHeight = 19.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        PrimaryGradientButton(
          text = if (isEnglish) "Prepare gift" else "Chuẩn bị quà",
          onClick = onOpenGifts,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox),
          testTag = "btn_readiness_gifts"
        )
        SecondaryButton(
          text = if (isEnglish) "View calendar" else "Xem lịch",
          onClick = onOpenCalendar,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox),
          testTag = "btn_readiness_calendar"
        )
      }
    }
  }
}

private fun formatReadinessDate(iso: String, isEnglish: Boolean): String {
  val parts = iso.trim().split("-")
  if (parts.size != 3) return iso
  val (y, m, d) = parts
  return if (isEnglish) "$m/$d/$y" else "$d/$m/$y"
}
