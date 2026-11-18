package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

object InLoveFonts {
  val DisplaySerif: FontFamily = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_semibold, FontWeight.SemiBold),
    Font(R.font.lora_bold, FontWeight.Bold),
  )

  val BodySans: FontFamily = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
  )
}

val Typography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = InLoveFonts.DisplaySerif,
      fontWeight = FontWeight.Bold,
      fontSize = 54.sp,
      lineHeight = 56.sp,
      letterSpacing = 0.sp,
      fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
      fontFamily = InLoveFonts.DisplaySerif,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      lineHeight = 28.sp,
      letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
      fontFamily = InLoveFonts.DisplaySerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 18.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = InLoveFonts.BodySans,
      fontWeight = FontWeight.Normal,
      fontSize = 15.sp,
      lineHeight = 21.sp,
      letterSpacing = 0.sp,
    ),
    // Default Material letter-spacing (0.1-0.5sp) makes Vietnamese labels wrap; drop it.
    bodyMedium = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = InLoveFonts.BodySans, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
  )
