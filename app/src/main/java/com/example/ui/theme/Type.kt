package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
      ),
    // Default Material letter-spacing (0.1-0.5sp) makes Vietnamese labels wrap; drop it.
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
  )
