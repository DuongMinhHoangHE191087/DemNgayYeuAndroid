package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.example.ui.theme.Motion

/** Scales the element to [pressedScale] while [source] is pressed, then springs back. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = Motion.PressedScale): Modifier = composed {
  val pressed by source.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) pressedScale else 1f,
    animationSpec = Motion.press()
  )
  graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/** Clickable with press-scale feedback. The scale and the ripple share one interaction source. */
fun Modifier.pressClickable(
  onClick: () -> Unit,
  enabled: Boolean = true,
  role: Role? = Role.Button,
  onClickLabel: String? = null,
): Modifier = composed {
  val source = remember { MutableInteractionSource() }
  pressScale(source).clickable(
    interactionSource = source,
    indication = LocalIndication.current,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick
  )
}
