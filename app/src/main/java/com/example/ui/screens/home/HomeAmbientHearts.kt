@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Gentle romantic ambient floating hearts drifting across the home dashboard background.
 */
@Composable
fun AmbientFloatingHeartsCanvasContent(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "ambient_hearts")
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 7000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "drift_progress"
  )

  Canvas(modifier = modifier) {
    val heartColors = listOf(
      Color(0xFFFFB6C1).copy(alpha = 0.40f),
      Color(0xFFFFC0CB).copy(alpha = 0.35f),
      Color(0xFFFFD1DC).copy(alpha = 0.42f),
      Color(0xFFF8BBD0).copy(alpha = 0.38f),
      Color(0xFFE1BEE7).copy(alpha = 0.30f),
      Color(0xFFFFE4E1).copy(alpha = 0.45f)
    )

    val particles = listOf(
      0.10f to 0.15f,
      0.24f to 0.65f,
      0.38f to 0.05f,
      0.52f to 0.85f,
      0.68f to 0.40f,
      0.82f to 0.70f,
      0.92f to 0.25f,
      0.30f to 0.45f,
      0.60f to 0.20f
    )

    particles.forEachIndexed { index, (xFrac, offsetFrac) ->
      val localProg = (progress + offsetFrac) % 1f
      val y = size.height * (1f - localProg)
      val sway = sin((localProg * 3.5 * Math.PI) + index).toFloat() * 18.dp.toPx()
      val x = size.width * xFrac + sway
      val heartSize = (14 + (index % 4) * 4).dp.toPx()
      drawHeartPath(
        center = Offset(x, y),
        size = heartSize,
        color = heartColors[index % heartColors.size]
      )
    }
  }
}

private fun DrawScope.drawHeartPath(
  center: Offset,
  size: Float,
  color: Color
) {
  val path = Path().apply {
    val half = size / 2f
    val topCurveHeight = size * 0.35f
    moveTo(center.x, center.y + half * 0.8f)
    cubicTo(
      center.x - half, center.y,
      center.x - half, center.y - topCurveHeight,
      center.x, center.y - topCurveHeight * 0.35f
    )
    cubicTo(
      center.x + half, center.y - topCurveHeight,
      center.x + half, center.y,
      center.x, center.y + half * 0.8f
    )
    close()
  }
  drawPath(path = path, color = color)
}
