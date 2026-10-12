package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil

private val LightColorScheme = lightColorScheme(
  primary = Primary,
  onPrimary = OnPrimary,
  primaryContainer = PrimaryContainer,
  onPrimaryContainer = OnPrimaryContainer,
  secondary = Secondary,
  onSecondary = OnSecondary,
  secondaryContainer = SecondaryContainer,
  onSecondaryContainer = OnSecondaryContainer,
  tertiary = Tertiary,
  onTertiary = OnTertiary,
  tertiaryContainer = TertiaryContainer,
  onTertiaryContainer = OnTertiaryContainer,
  background = Surface,
  onBackground = OnSurface,
  surface = Surface,
  onSurface = OnSurface,
  surfaceVariant = SurfaceVariant,
  onSurfaceVariant = OnSurfaceVariant,
  surfaceContainer = SurfaceContainer,
  surfaceContainerHigh = SurfaceContainerHigh,
  surfaceContainerHighest = SurfaceContainerHighest,
  surfaceContainerLow = SurfaceContainerLow,
  surfaceContainerLowest = SurfaceContainerLowest,
  outline = Outline,
  outlineVariant = OutlineVariant,
  inverseSurface = InverseSurface,
  inverseOnSurface = InverseOnSurface,
  inversePrimary = InversePrimary
)

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryContainer,
  onPrimary = Color.White,
  primaryContainer = Primary,
  onPrimaryContainer = Color.White,
  secondary = Secondary,
  onSecondary = Color.White,
  secondaryContainer = SecondaryContainer,
  onSecondaryContainer = Color.White,
  tertiary = TertiaryContainer,
  onTertiary = Color.White,
  background = Color(0xFF1E0815),
  onBackground = Color(0xFFFFF0F5),
  surface = Color(0xFF2B0E1E),
  onSurface = Color(0xFFFFF0F5),
  surfaceVariant = Color(0xFF4A1934),
  onSurfaceVariant = Color(0xFFFFD8E6),
  outline = Color(0xFFFF80AB),
  outlineVariant = Color(0xFF8A2E5B)
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(28.dp),
  extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Always prioritize a crisp, vibrant, romantic Light Mode UI
  val colorScheme = LightColorScheme

  AppDensity {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = AppShapes,
      content = content
    )
  }
}

/**
 * Shrinks every dp/sp uniformly so more content fits on a phone screen; system font scale is kept.
 * A Dialog window resets LocalDensity, so full-screen dialogs must wrap their content in this too.
 */
@Composable
fun AppDensity(content: @Composable () -> Unit) {
  val base = LocalDensity.current
  CompositionLocalProvider(
    LocalDensity provides Density(base.density * UI_SCALE, base.fontScale),
    content = content
  )
}

/** Global UI scale. 1f = stock sizes; lower = denser. Tune here only. */
private const val UI_SCALE = 0.85f

/** Min tap box in layout dp: renders at >= 48dp after AppDensity. */
val MinTouchBox: Dp = ceil(48f / UI_SCALE).dp
