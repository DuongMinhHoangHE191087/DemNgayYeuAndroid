@file:Suppress("FunctionName")
package com.example.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Standard elevated pearl-white card surface matching DESIGN.md.
 */
@Composable
fun InLoveSurfaceCard(
  modifier: Modifier = Modifier,
  containerColor: Color = InLoveColors.WarmPearl.copy(alpha = 0.96f),
  borderColor: Color = InLoveColors.BorderDelicate,
  cornerRadius: Dp = InLoveRadii.Card,
  elevation: Dp = 3.dp,
  contentPadding: Dp = InLoveSpacing.Md,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    shape = RoundedCornerShape(cornerRadius),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(InLoveBorders.Card, borderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(InLoveSpacing.Xs),
      content = content,
    )
  }
}

/**
 * Soft blush container card used for highlighted sections and forms inside modals/screens.
 */
@Composable
fun InLoveTintedCard(
  modifier: Modifier = Modifier,
  containerColor: Color = InLoveColors.SoftBlushCard,
  borderColor: Color = InLoveColors.BorderSoftPink,
  cornerRadius: Dp = InLoveRadii.Chip,
  contentPadding: Dp = InLoveSpacing.Sm,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    shape = RoundedCornerShape(cornerRadius),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(InLoveBorders.Hairline, borderColor),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(InLoveSpacing.Xs),
      content = content,
    )
  }
}

/**
 * Cohesive section header with optional circular tinted icon badge, high-contrast title, and optional trailing slot.
 */
@Composable
fun InLoveSectionHeader(
  title: String,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  iconTint: Color = Primary,
  iconContainerColor: Color = Primary.copy(alpha = 0.12f),
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (icon != null) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(iconContainerColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = InLoveTypography.SectionHeader,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = InLoveTypography.CardSubtitle.copy(fontSize = 11.5.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    if (trailing != null) {
      trailing()
    }
  }
}

/**
 * Stadium-shaped status pill with AAA contrast on light or accent surfaces.
 */
@Composable
fun InLoveStatusPill(
  text: String,
  modifier: Modifier = Modifier,
  containerColor: Color = InLoveColors.RoseTint,
  contentColor: Color = InLoveColors.RoseHighlight,
  borderColor: Color? = InLoveColors.BorderDelicate,
  icon: ImageVector? = null,
) {
  Surface(
    shape = RoundedCornerShape(InLoveRadii.Pill),
    color = containerColor,
    border = borderColor?.let { BorderStroke(InLoveBorders.Hairline, it) },
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(13.dp),
        )
      }
      Text(
        text = text,
        color = contentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Reusable progress bar block with percentage readout (used for checklists & dossier completeness).
 */
@Composable
fun InLoveProgressBar(
  label: String,
  progressFraction: Float,
  modifier: Modifier = Modifier,
  trailingLabel: String = "${(progressFraction * 100f).toInt().coerceIn(0, 100)}%",
  barColor: Color = Primary,
) {
  val clamped = progressFraction.coerceIn(0f, 1f)
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSubtlePlum,
      )
      Text(
        text = trailingLabel,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextDarkPlum,
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
      progress = { clamped },
      modifier = Modifier
        .fillMaxWidth()
        .height(7.dp)
        .clip(RoundedCornerShape(InLoveRadii.Pill)),
      color = barColor,
      trackColor = InLoveColors.RoseTintTrack,
      strokeCap = StrokeCap.Round,
    )
  }
}
