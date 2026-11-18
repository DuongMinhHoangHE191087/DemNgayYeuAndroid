@file:Suppress("FunctionName")
package com.example.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.pressClickable
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Canonical high-contrast OutlinedTextField color palette shared across all screens and dialogs.
 * This is the single allowed call site for `OutlinedTextFieldDefaults.colors(` in the UI layer.
 */
@Composable
fun inLoveOutlinedTextFieldColors(
  focusedBorderColor: Color = Primary,
  unfocusedBorderColor: Color = InLoveColors.InputBorder,
  focusedContainerColor: Color = Color.White,
  unfocusedContainerColor: Color = Color.White,
  disabledContainerColor: Color = InLoveColors.InputDisabledBg,
  focusedLabelColor: Color = Primary,
  unfocusedLabelColor: Color = TextSubtlePlum,
  focusedPlaceholderColor: Color = TextSubtlePlum.copy(alpha = 0.78f),
  unfocusedPlaceholderColor: Color = TextSubtlePlum.copy(alpha = 0.65f),
  cursorColor: Color = Primary,
) = OutlinedTextFieldDefaults.colors(
  focusedTextColor = TextDarkPlum,
  unfocusedTextColor = TextDarkPlum,
  focusedContainerColor = focusedContainerColor,
  unfocusedContainerColor = unfocusedContainerColor,
  disabledContainerColor = disabledContainerColor,
  focusedBorderColor = focusedBorderColor,
  unfocusedBorderColor = unfocusedBorderColor,
  focusedLabelColor = focusedLabelColor,
  unfocusedLabelColor = unfocusedLabelColor,
  focusedPlaceholderColor = focusedPlaceholderColor,
  unfocusedPlaceholderColor = unfocusedPlaceholderColor,
  cursorColor = cursorColor,
)

/**
 * Unified text field styled with InLove design tokens and WCAG AAA text contrast.
 */
@Composable
fun InLoveTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  singleLine: Boolean = true,
  maxLines: Int = if (singleLine) 1 else 4,
  minLines: Int = 1,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  shape: Shape = RoundedCornerShape(InLoveRadii.Input),
  testTag: String? = null,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    placeholder = placeholder?.let { hint -> { Text(hint) } },
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    supportingText = supportingText,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    shape = shape,
    colors = inLoveOutlinedTextFieldColors(),
    modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
  )
}

/**
 * 48dp+ minimum touch target choice chip for love languages, interests, tones, and holiday filters.
 */
@Composable
fun InLoveChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  testTag: String? = null,
) {
  val bg by animateColorAsState(
    targetValue = if (selected) Primary else InLoveColors.RoseTint,
    label = "choice_chip_bg",
  )
  val border by animateColorAsState(
    targetValue = if (selected) Primary else InLoveColors.BorderDelicate,
    label = "choice_chip_border",
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) Color.White else TextDarkPlum,
    label = "choice_chip_fg",
  )
  val shape = RoundedCornerShape(InLoveRadii.Pill)

  Surface(
    shape = shape,
    color = bg,
    border = BorderStroke(InLoveBorders.Hairline, border),
    modifier = modifier
      .widthIn(min = MinTouchBox)
      .heightIn(min = MinTouchBox)
      .clip(shape)
      .pressClickable(onClick = onClick)
      .let { if (testTag != null) it.testTag(testTag) else it },
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(16.dp),
        )
      }
      Text(
        text = label,
        color = contentColor,
        fontSize = 12.5.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
