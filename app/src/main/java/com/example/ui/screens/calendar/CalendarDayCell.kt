@file:Suppress("FunctionName")
package com.example.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OnSurface
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLow
import java.util.Locale

@Composable
internal fun CalendarDayCell(
  dayNum: Int,
  isToday: Boolean,
  isSelected: Boolean,
  isMilestoneDay: Boolean,
  isCurrentMonth: Boolean,
  isSunday: Boolean,
  onClick: () -> Unit
) {
  val alpha = if (isCurrentMonth) 1f else 0.35f
  Box(
    modifier = Modifier
      .width(38.dp)
      .height(48.dp)
      .clip(RoundedCornerShape(14.dp))
      .then(
        when {
          isMilestoneDay && isCurrentMonth -> Modifier
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(RoseGradientStart, RoseGradientMid)))
          isSelected && isCurrentMonth -> Modifier
            .background(PrimaryFixed.copy(alpha = 0.7f))
            .border(1.5.dp, Primary, RoundedCornerShape(14.dp))
          isToday && isCurrentMonth -> Modifier
            .background(SurfaceContainerLow)
            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
          else -> Modifier
        }
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = String.format(Locale.ROOT, "%02d", dayNum),
        fontSize = 13.sp,
        fontWeight = if ((isMilestoneDay || isToday || isSelected) && isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
        color = when {
          isMilestoneDay && isCurrentMonth -> Color.White
          isSunday && isCurrentMonth -> Secondary
          else -> OnSurface.copy(alpha = alpha)
        }
      )
      if (isMilestoneDay && isCurrentMonth) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(10.dp)
        )
      } else if (isToday && isCurrentMonth) {
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Secondary)
        )
      } else {
        Spacer(modifier = Modifier.size(5.dp))
      }
    }
  }
}
