@file:Suppress("FunctionName")
package com.example.ui.screens.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.guide.GuideChapter
import com.example.shared.guide.GuideQuickAction
import com.example.shared.guide.GuideStep
import com.example.shared.guide.InLoveGuideCatalog
import com.example.ui.components.AppDialog
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveChoiceChip
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.InLoveTypography
import com.example.ui.theme.CardBorder
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings

/**
 * Interactive 4-chapter InLove User Guide backed by `:shared`'s [InLoveGuideCatalog].
 */
@Composable
fun InLoveUserGuideDialog(
  language: AppLanguage,
  onDismiss: () -> Unit,
  onQuickAction: (GuideQuickAction) -> Unit = {},
) {
  val isEnglish = language == AppLanguage.EN
  val strings = LocalizedStrings.get(language)
  var selectedId by remember { mutableStateOf(InLoveGuideCatalog.CHAPTERS.first().id) }
  val activeChapter = InLoveGuideCatalog.chapterFor(selectedId)

  AppDialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(InLoveRadii.Sheet),
      color = Color.White.copy(alpha = 0.99f),
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .animateDialogEntrance()
        .border(1.2.dp, CardBorder, RoundedCornerShape(InLoveRadii.Sheet))
        .testTag("inlove_user_guide_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (isEnglish) "InLove Guide" else "Hướng Dẫn InLove",
              style = InLoveTypography.ChapterHeadline,
            )
            Text(
              text = if (isEnglish) "Four quiet essentials to enjoy together" else "Bốn bước gọn gàng để hai bạn bắt đầu",
              fontSize = 12.sp,
              color = TextSubtlePlum,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(MinTouchBox).testTag("btn_dismiss_guide_x")
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = if (isEnglish) "Close guide" else "Đóng hướng dẫn",
              tint = TextSubtlePlum
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          InLoveGuideCatalog.CHAPTERS.forEach { chapter ->
            InLoveChoiceChip(
              label = chapter.shortTab(isEnglish),
              selected = chapter.id == selectedId,
              onClick = { selectedId = chapter.id },
              testTag = "guide_chapter_${chapter.id.name}"
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          GuideChapterSection(
            chapter = activeChapter,
            isEnglish = isEnglish,
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          SecondaryButton(
            text = activeChapter.quickActionLabel(isEnglish),
            onClick = { onQuickAction(activeChapter.quickAction) },
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MinTouchBox),
            testTag = "guide_quick_action"
          )
          PrimaryGradientButton(
            text = strings.btnGotIt,
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MinTouchBox),
            testTag = "btn_close_guide"
          )
        }
      }
    }
  }
}

@Composable
private fun GuideChapterSection(
  chapter: GuideChapter,
  isEnglish: Boolean,
) {
  Text(
    text = chapter.title(isEnglish),
    fontFamily = InLoveFonts.DisplaySerif,
    fontSize = 18.sp,
    fontWeight = FontWeight.SemiBold,
    color = TextDarkPlum,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
  )
  Text(
    text = chapter.summary(isEnglish),
    fontSize = 12.5.sp,
    color = TextSubtlePlum,
    lineHeight = 18.sp,
  )
  Spacer(modifier = Modifier.height(2.dp))
  chapter.steps.forEach { step ->
    GuideStepCard(step = step, isEnglish = isEnglish)
  }
}

@Composable
private fun GuideStepCard(
  step: GuideStep,
  isEnglish: Boolean,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    colors = CardDefaults.cardColors(containerColor = InLoveColors.SoftBlushCard),
    border = BorderStroke(1.dp, CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .clip(CircleShape)
          .background(InLoveColors.RoseTint)
          .border(1.dp, CardBorder, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = step.numberLabel,
          style = TextStyle(
            fontFamily = InLoveFonts.BodySans,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
            color = Primary,
          )
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = step.title(isEnglish),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = step.detail(isEnglish),
          fontSize = 12.sp,
          color = TextSubtlePlum,
          lineHeight = 17.sp,
        )
      }
    }
  }
}
