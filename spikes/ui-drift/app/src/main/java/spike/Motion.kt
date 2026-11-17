package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Central motion tokens. Tune feel here; call sites reference these instead of literals. */
object Motion {
  /** Scale of a tappable surface while pressed. */
  const val PressedScale = 0.96f

  /** Press-in and release spring. */
  fun <T> press(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
  )
}
