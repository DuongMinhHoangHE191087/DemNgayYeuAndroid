@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Large circular Days-in-Love counter with zero-recomposition DrawScope aura sweep.
 */
@Composable
fun HomeDaysCounterHero(
  loveDays: Int,
  loveTitle: String,
  daysInLoveLabel: String,
  isEnglish: Boolean,
  counterBoxSize: Dp,
  counterSurfaceSize: Dp,
  daysTitleFontSize: TextUnit,
  daysCountFontSize: TextUnit,
  daysCountLineHeight: TextUnit,
  daysInLoveLabelSize: TextUnit,
  onCounterClick: () -> Unit,
  onEditTitleClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "counter_aura")
  val counterAuraAngle: State<Float> = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(12000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "counter_aura_angle"
  )
  val animatedLoveDays: State<Int> = animateIntAsState(
    targetValue = loveDays,
    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    label = "animated_love_days"
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(counterBoxSize)
      .clip(CircleShape)
      .clickable(onClick = onCounterClick)
      .testTag("love_circle_days_counter")
  ) {
    // Read animated angle inside Canvas DrawScope to avoid per-frame recompositions.
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = 5.dp.toPx()
      drawCircle(
        color = Color(0xFFFFE4EE).copy(alpha = 0.85f),
        radius = size.minDimension / 2 - strokeWidth / 2,
        style = Stroke(width = strokeWidth)
      )
      drawArc(
        brush = Brush.sweepGradient(
          listOf(
            Color(0xFFFFB6C1),
            Color(0xFFFF4081),
            Color(0xFFFF80AB),
            Color(0xFFF48FB1),
            Color(0xFFFFB6C1)
          )
        ),
        startAngle = counterAuraAngle.value,
        sweepAngle = 280f,
        useCenter = false,
        style = Stroke(width = strokeWidth + 2.dp.toPx(), cap = StrokeCap.Round)
      )
    }

    Surface(
      shape = CircleShape,
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 8.dp,
      modifier = Modifier.size(counterSurfaceSize)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .widthIn(max = counterSurfaceSize - 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onEditTitleClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
          Text(
            text = loveTitle,
            fontFamily = InLoveFonts.DisplaySerif,
            fontSize = daysTitleFontSize,
            fontWeight = FontWeight.Bold,
            color = Primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = "${animatedLoveDays.value}",
          style = TextStyle(
            fontFamily = InLoveFonts.DisplaySerif,
            fontSize = daysCountFontSize,
            lineHeight = daysCountLineHeight,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
            color = TextDarkPlum,
          )
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = daysInLoveLabel,
          fontSize = daysInLoveLabelSize,
          fontWeight = FontWeight.SemiBold,
          color = TextSubtlePlum
        )
      }
    }
  }
}
