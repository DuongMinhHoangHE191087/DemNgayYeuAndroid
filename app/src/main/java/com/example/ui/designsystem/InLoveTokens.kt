package com.example.ui.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceBright
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/**
 * Corner radii tokens aligned with DESIGN.md.
 */
object InLoveRadii {
  val Badge = 12.dp
  val Input = 12.dp
  val InputSoft = 14.dp
  val Chip = 20.dp
  val Card = 24.dp
  val CardLarge = 26.dp
  val Sheet = 28.dp
  val Pill = 50.dp
}

/**
 * Spacing scale ensuring >= 8.dp gaps between touch targets.
 */
object InLoveSpacing {
  val Xxs = 4.dp
  val Xs = 8.dp
  val Sm = 12.dp
  val Md = 16.dp
  val Lg = 20.dp
  val Xl = 24.dp
}

/**
 * Uniform 16.dp outer screen margin & 32.dp inner card content keyline across screens.
 */
object InLoveLayoutGrid {
  val ScreenHorizontal = 16.dp
  val CardPadding = 16.dp
  val CardVerticalGap = 12.dp
  val ItemGap = 8.dp
}

/**
 * Standard stroke widths across cards, chips, and inputs.
 */
object InLoveBorders {
  val Hairline = 1.dp
  val Card = 1.2.dp
  val Emphasis = 1.5.dp
  val Selected = 2.dp
}

/**
 * Semantic high-contrast palette tokens grounded in TextDarkPlum (#1F0416) and TextSubtlePlum (#4A1934).
 */
object InLoveColors {
  val TextPrimary: Color = TextDarkPlum
  val TextSecondary: Color = TextSubtlePlum
  val RoseAccent: Color = Primary
  val RoseHighlight: Color = Color(0xFF880E4F)
  val RoseBright: Color = PrimaryContainer
  val RoseSecondary: Color = Secondary
  val RoseFixed: Color = PrimaryFixed

  val ScreenSurface: Color = Surface
  val WarmPearl: Color = SurfaceBright
  val SoftBlushCard: Color = Color(0xFFFFF7F9)
  val RoseTint: Color = SurfaceContainerLow
  val RoseTintMedium: Color = SurfaceContainer
  val RoseTintHigh: Color = SurfaceContainerHigh
  val RoseTintTrack: Color = SurfaceContainerHighest

  val BorderDelicate: Color = CardBorder
  val BorderSoftPink: Color = Color(0xFFFFC0D3)
  val BorderRose: Color = Color(0xFFFFDDE6)
  val InputBorder: Color = Color(0xFFD8B8C8)
  val InputDisabledBg: Color = Color(0xFFFAF3F7)

  val SuccessBg: Color = Color(0xFFE8F5E9)
  val SuccessBorder: Color = Color(0xFF81C784)
  val SuccessText: Color = Color(0xFF1B5E20)

  val WarningBg: Color = Color(0xFFFFF8E1)
  val WarningBorder: Color = Color(0xFFFFB74D)
  val WarningText: Color = Color(0xFFE65100)

  val DangerBg: Color = Color(0xFFFFEBEE)
  val DangerBorder: Color = Color(0xFFFFCDD2)
  val DangerText: Color = Color(0xFFD32F2F)

  val LavenderCardBg: Color = Color(0xFFFAF5FF)
  val LavenderBorder: Color = Color(0xFFE1BEE7)

  val RoseHorizontalGradient: Brush = Brush.horizontalGradient(
    listOf(RoseGradientStart, RoseGradientMid, RoseGradientEnd)
  )
}

/**
 * Typography helpers matching DESIGN.md contrast & hierarchy.
 */
object InLoveTypography {
  val DisplayHero = TextStyle(
    fontFamily = InLoveFonts.DisplaySerif,
    fontSize = 54.sp,
    lineHeight = 56.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    color = TextDarkPlum,
  )

  val ChapterHeadline = TextStyle(
    fontFamily = InLoveFonts.DisplaySerif,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.Bold,
    color = TextDarkPlum,
  )

  val EditorialQuote = TextStyle(
    fontFamily = InLoveFonts.DisplaySerif,
    fontSize = 15.sp,
    lineHeight = 23.sp,
    fontWeight = FontWeight.Normal,
    color = TextDarkPlum,
  )

  val TabularMetric = TextStyle(
    fontFamily = InLoveFonts.BodySans,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    color = Primary,
  )

  val SectionHeader = TextStyle(
    fontFamily = InLoveFonts.DisplaySerif,
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    color = TextDarkPlum,
    letterSpacing = 0.sp,
  )

  val CategoryOverline = TextStyle(
    fontFamily = InLoveFonts.BodySans,
    fontSize = 11.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Primary,
    letterSpacing = 0.8.sp,
  )

  val CardTitle = TextStyle(
    fontFamily = InLoveFonts.BodySans,
    fontSize = 15.sp,
    fontWeight = FontWeight.Bold,
    color = TextDarkPlum,
  )

  val CardBody = TextStyle(
    fontFamily = InLoveFonts.BodySans,
    fontSize = 13.sp,
    fontWeight = FontWeight.Normal,
    color = TextDarkPlum,
    lineHeight = 19.sp,
  )

  val CardSubtitle = TextStyle(
    fontFamily = InLoveFonts.BodySans,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    color = TextSubtlePlum,
    lineHeight = 17.sp,
  )

  val BadgeLabel = TextStyle(
    fontFamily = InLoveFonts.BodySans,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    color = Primary,
  )
}
