@file:Suppress("FunctionName")
package com.example.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MilestoneEntity
import com.example.shared.calendar.AnniversaryBehaviorEngine
import com.example.shared.calendar.MonthiversaryOccurrence
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.InLoveStatusPill
import com.example.ui.designsystem.InLoveTypography
import com.example.ui.theme.CardBorder
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import java.text.NumberFormat
import java.util.Locale

private val EN_MONTH_SHORT = arrayOf(
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun formatIsoForDisplay(iso: String, isEnglish: Boolean): String {
  val parts = iso.trim().split("-")
  if (parts.size != 3) return iso
  val year = parts[0]
  val month = parts[1].toIntOrNull() ?: return iso
  val day = parts[2].toIntOrNull() ?: return iso
  if (month !in 1..12 || day !in 1..31) return iso
  return if (isEnglish) {
    "${EN_MONTH_SHORT[month - 1]} $day, $year"
  } else {
    "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
  }
}

private fun formatCount(value: Int, isEnglish: Boolean): String {
  val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("vi-VN")
  return NumberFormat.getIntegerInstance(locale).format(value)
}

/**
 * Localizes canonical & legacy milestone tags so Vietnamese UI never leaks raw English enum labels
 * (e.g. "LOVE", "MILESTONE", "100 Days") and English UI renders clean English labels.
 */
fun localizeMilestoneTag(raw: String, isEnglish: Boolean): String {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return ""
  return when (trimmed.lowercase(Locale.ROOT)) {
    "love", "tình yêu" -> if (isEnglish) "Love" else "Tình yêu"
    "milestone", "cột mốc" -> if (isEnglish) "Milestone" else "Cột mốc"
    "anniversary", "kỷ niệm" -> if (isEnglish) "Anniversary" else "Kỷ niệm"
    "holiday", "ngày lễ" -> if (isEnglish) "Holiday" else "Ngày lễ"
    "date", "hẹn hò" -> if (isEnglish) "Date" else "Hẹn hò"
    "travel", "du lịch" -> if (isEnglish) "Travel" else "Du lịch"
    "birthday", "sinh nhật" -> if (isEnglish) "Birthday" else "Sinh nhật"
    "100 days", "100 ngày" -> if (isEnglish) "100 days" else "100 ngày"
    "1 year", "1 năm" -> if (isEnglish) "1 year" else "1 năm"
    "ý nghĩa" -> if (isEnglish) "Meaningful" else "Ý nghĩa"
    else -> trimmed
  }
}

/**
 * Highlights romantic milestones (520, 1000, 1314), Monthiversary countdown, and Thất Tịch (7/7 Lunar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecialMilestonesHighlightStrip(
  loveDays: Int,
  nextMonthiversary: MonthiversaryOccurrence?,
  nextThatTichIso: String?,
  isEnglish: Boolean,
  modifier: Modifier = Modifier,
) {
  val keyDayMilestones = remember(loveDays) {
    listOf(520, 1000, 1314).map { target ->
      val delta = (target - loveDays).coerceAtLeast(0)
      target to delta
    }
  }

  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
    border = BorderStroke(1.dp, CardBorder),
    modifier = modifier
      .fillMaxWidth()
      .testTag("calendar_special_milestones_strip")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = if (isEnglish) "Milestones & Lunar Calendar" else "Cột Mốc Tình Yêu & Lịch Âm",
        style = InLoveTypography.SectionHeader,
      )

      Spacer(modifier = Modifier.height(10.dp))

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        keyDayMilestones.forEach { (daysTarget, remaining) ->
          val targetFormatted = formatCount(daysTarget, isEnglish)
          val remainingFormatted = formatCount(remaining, isEnglish)
          val label = when {
            remaining == 0 && loveDays >= daysTarget -> if (isEnglish) "$targetFormatted days · Reached" else "$targetFormatted ngày · Đã qua"
            else -> if (isEnglish) "$targetFormatted days · $remainingFormatted left" else "$targetFormatted ngày · còn $remainingFormatted ngày"
          }
          InLoveStatusPill(
            text = label,
            containerColor = if (loveDays >= daysTarget) InLoveColors.SuccessBg else InLoveColors.RoseTint,
            contentColor = if (loveDays >= daysTarget) InLoveColors.SuccessText else InLoveColors.RoseHighlight,
            borderColor = if (loveDays >= daysTarget) InLoveColors.SuccessBorder else CardBorder
          )
        }

        if (nextMonthiversary != null) {
          val mText = if (isEnglish) {
            "${nextMonthiversary.months} months · ${nextMonthiversary.daysUntil} days left"
          } else {
            "Tròn ${nextMonthiversary.months} tháng · còn ${nextMonthiversary.daysUntil} ngày"
          }
          InLoveStatusPill(
            text = mText,
            containerColor = InLoveColors.LavenderCardBg,
            contentColor = TextDarkPlum,
            borderColor = InLoveColors.LavenderBorder
          )
        }

        if (!nextThatTichIso.isNullOrBlank()) {
          val displayDate = formatIsoForDisplay(nextThatTichIso, isEnglish)
          val tText = if (isEnglish) "Qixi 7/7 Lunar · $displayDate" else "Thất Tịch 7/7 ÂL · $displayDate"
          InLoveStatusPill(
            text = tText,
            containerColor = InLoveColors.RoseTint,
            contentColor = InLoveColors.RoseHighlight,
            borderColor = InLoveColors.BorderSoftPink
          )
        }
      }
    }
  }
}

/**
 * Preparation lead-time & booking guidance chip attached to upcoming milestones.
 */
@Composable
fun MilestoneLeadTimeHint(
  milestone: MilestoneEntity,
  isEnglish: Boolean,
  modifier: Modifier = Modifier,
) {
  if (milestone.isPast) return
  val profile = remember(milestone.title) {
    AnniversaryBehaviorEngine.profileForTitleOrId(milestone.title)
  }
  val leadDays = profile.recommendedLeadDays.firstOrNull { it >= milestone.daysRemaining }
    ?: profile.recommendedLeadDays.maxOrNull()
    ?: 7

  val hintText = when {
    milestone.daysRemaining <= 1 -> if (isEnglish) "Ready today · Handwritten note" else "Chuẩn bị hôm nay · Thiệp viết tay"
    profile.bookingAdvised -> if (isEnglish) "Book ${leadDays}d ahead" else "Nên đặt trước $leadDays ngày"
    else -> if (isEnglish) "Prep ${leadDays}d ahead" else "Chuẩn bị trước $leadDays ngày"
  }

  Surface(
    shape = RoundedCornerShape(InLoveRadii.Badge),
    color = if (profile.bookingAdvised) InLoveColors.WarningBg else InLoveColors.RoseTint,
    border = BorderStroke(1.dp, if (profile.bookingAdvised) InLoveColors.WarningBorder else CardBorder),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = hintText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (profile.bookingAdvised) InLoveColors.WarningText else TextSubtlePlum
      )
    }
  }
}
