@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveLayoutGrid
import com.example.ui.theme.CardBorder
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
internal fun HomeNextMilestoneCard(
  loveDays: Int,
  nextMilestone: Int,
  daysRemaining: Int,
  milestoneProgress: Float,
  isMilestoneReached: Boolean,
  titleFormat: String,
  daysRemainingFormat: String,
  progressTowardsLabel: String,
  giftButtonLabel: String,
  calendarButtonLabel: String,
  celebrationTitle: String,
  celebrationButtonLabel: String,
  onOpenGifts: () -> Unit,
  onOpenCalendar: () -> Unit,
  onCelebrate: () -> Unit,
) {
  val animatedMilestoneProgress by animateFloatAsState(
    targetValue = milestoneProgress,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "animated_milestone_progress"
  )

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
    border = BorderStroke(1.2.dp, CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(InLoveLayoutGrid.CardPadding)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = String.format(titleFormat, nextMilestone),
          fontFamily = InLoveFonts.DisplaySerif,
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDarkPlum,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFFFF0F5),
          border = BorderStroke(1.dp, CardBorder)
        ) {
          Text(
            text = String.format(daysRemainingFormat, daysRemaining),
            style = TextStyle(
              fontFamily = InLoveFonts.BodySans,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              fontFeatureSettings = "tnum",
              color = Primary,
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = progressTowardsLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSubtlePlum)
        Text(
          text = "${(milestoneProgress * 100).toInt()}% ($loveDays / $nextMilestone)",
          style = TextStyle(
            fontFamily = InLoveFonts.BodySans,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
            color = Primary,
          )
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      LinearProgressIndicator(
        progress = { animatedMilestoneProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(50.dp)),
        color = Primary,
        trackColor = Color(0xFFFFD1DF)
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryGradientButton(
          text = giftButtonLabel,
          onClick = onOpenGifts,
          modifier = Modifier.weight(1f).heightIn(min = MinTouchBox),
          icon = Icons.Filled.CardGiftcard,
          testTag = "btn_next_anniversary_gifts"
        )
        SecondaryButton(
          text = calendarButtonLabel,
          onClick = onOpenCalendar,
          modifier = Modifier.weight(1f).heightIn(min = MinTouchBox),
          icon = Icons.Filled.CalendarMonth,
          testTag = "btn_next_anniversary_calendar"
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Button(
        onClick = onCelebrate,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox)
          .testTag("btn_celebrate_milestone_hearts")
      ) {
        Icon(Icons.Filled.Celebration, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isMilestoneReached) celebrationTitle else celebrationButtonLabel,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
