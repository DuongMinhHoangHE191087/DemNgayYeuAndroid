@file:Suppress("FunctionName")
package com.example.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.AppDialog
import com.example.ui.components.animateDialogEntrance
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Shared modal header featuring a romantic gradient icon circle, bold plum title, and 48dp+ close button.
 */
@Composable
fun InLoveModalHeader(
  title: String,
  icon: ImageVector,
  onDismiss: () -> Unit,
  closeContentDescription: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  closeTestTag: String? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(RoseGradientStart, RoseGradientEnd))),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(17.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            fontSize = 11.5.sp,
            color = TextSubtlePlum,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    IconButton(
      onClick = onDismiss,
      modifier = Modifier
        .size(MinTouchBox)
        .let { if (closeTestTag != null) it.testTag(closeTestTag) else it },
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = closeContentDescription,
        tint = TextSubtlePlum,
      )
    }
  }
}

/**
 * Standard elevated dialog card hosted inside [AppDialog] so theme density scaling stays consistent.
 */
@Composable
fun InLoveDialogCard(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  usePlatformDefaultWidth: Boolean = true,
  borderColor: Color = InLoveColors.BorderRose,
  content: @Composable ColumnScope.() -> Unit,
) {
  AppDialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth),
  ) {
    Card(
      shape = RoundedCornerShape(InLoveRadii.Card),
      colors = CardDefaults.cardColors(containerColor = InLoveColors.WarmPearl),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      border = BorderStroke(InLoveBorders.Hairline, borderColor),
      modifier = modifier
        .widthIn(max = 380.dp)
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .padding(vertical = 12.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
      )
    }
  }
}

/**
 * Spacious scrollable modal scaffold for deep editors (Partner Dossier, Wishlist, Concierge details).
 */
@Composable
fun InLoveSheetModal(
  title: String,
  icon: ImageVector,
  onDismiss: () -> Unit,
  closeContentDescription: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  closeTestTag: String? = null,
  footer: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  AppDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(InLoveRadii.Sheet),
      color = InLoveColors.WarmPearl,
      shadowElevation = 14.dp,
      border = BorderStroke(InLoveBorders.Card, InLoveColors.BorderDelicate),
      modifier = modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth(0.94f)
        .fillMaxHeight(0.88f)
        .animateDialogEntrance(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        InLoveModalHeader(
          title = title,
          subtitle = subtitle,
          icon = icon,
          onDismiss = onDismiss,
          closeContentDescription = closeContentDescription,
          closeTestTag = closeTestTag,
        )
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          content = content,
        )
        if (footer != null) {
          footer()
        }
      }
    }
  }
}
