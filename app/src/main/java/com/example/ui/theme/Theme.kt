package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Always prioritize a crisp, vibrant, romantic Light Mode UI
  val colorScheme = LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content
  )
}
