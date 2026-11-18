@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveTypography
import com.example.ui.theme.CardBorder
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.AppLanguage

/**
 * Daily couple reflections card styled with Lora editorial serif typography.
 */
@Composable
fun HomeCoupleReflectionsCard(
  loveDays: Int,
  appLanguage: AppLanguage,
  sectionTag: String,
  sectionTitle: String,
  shuffleLabel: String,
  sendLabel: String,
  onShuffle: () -> Unit,
  onSend: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val loveQuotes = remember(loveDays, appLanguage) {
    if (appLanguage == AppLanguage.VI) {
      listOf(
        "Dù ngày mai có bận rộn đến đâu, anh vẫn chọn lắng nghe và yêu em như những ngày đầu tiên.",
        "Cảm ơn em đã cùng anh đi qua $loveDays ngày bình yên, qua những bữa cơm tối và cái ôm cuối ngày.",
        "Tình yêu bền lâu không phải là không bao giờ giận hờn, mà là luôn tìm cách quay về bên nhau sau mỗi lần hiểu lầm.",
        "Bên cạnh em, những khoảnh khắc giản dị nhất như đi dạo cuối tuần cũng trở thành điều anh mong đợi.",
        "Cảm ơn em đã kiên nhẫn bước vào cuộc sống của anh bằng tất cả sự dịu dàng.",
        "$loveDays ngày bên nhau không chỉ là một con số, mà là từng bước nhỏ mình cùng giữ lời hứa với nhau.",
        "Không cần dịp thật lớn mới nói lời thương, chỉ cần mỗi sáng thức dậy vẫn thấy hai đứa còn nhớ về nhau.",
        "Anh thích cách hai đứa có thể im lặng ngồi cạnh nhau hàng giờ mà vẫn thấy lòng nhẹ tênh.",
        "Mỗi mùa đi qua, điều anh trân trọng nhất vẫn là được nhìn em cười trong những câu chuyện nhỏ hằng ngày.",
        "Cảm ơn vì đã tin tưởng và nắm tay anh cả những lúc mọi thứ chưa thật sự dễ dàng.",
        "Chỉ cần biết tối nay vẫn có em chờ chia sẻ chuyện vui buồn, cả ngày dài bỗng trở nên dịu lại.",
        "Hành trình dài hay ngắn không quan trọng bằng việc mỗi chặng đường đều có dấu chân của cả hai."
      )
    } else {
      listOf(
        "No matter how busy tomorrow gets, I still choose to listen and care for you like day one.",
        "Thank you for walking with me through $loveDays quiet days, shared dinners, and warm evening talks.",
        "Lasting love is not the absence of small misunderstandings, but always finding our way back to kindness.",
        "Beside you, even an ordinary weekend walk feels like something worth remembering.",
        "Thank you for stepping into my life with patience, warmth, and quiet steadiness.",
        "$loveDays days together is not just a count on a screen, it is every promise we kept along the way.",
        "We do not need a grand occasion to say we care, just remembering the small things each morning.",
        "I love that we can sit together in quiet company for hours and still feel completely at home.",
        "With every passing season, my favorite moments are still hearing you laugh over everyday stories.",
        "Thank you for holding my hand through the uncertain days as faithfully as the bright ones.",
        "Knowing I can tell you about my day at the end of it makes every hard hour softer.",
        "The distance ahead matters less when every milestone carries both of our footprints."
      )
    }
  }
  val currentQuoteIndex = remember { mutableIntStateOf(0) }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
    border = BorderStroke(1.dp, CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = sectionTitle,
          fontFamily = InLoveFonts.DisplaySerif,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum,
          modifier = Modifier.weight(1f)
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFFFF0F5),
          border = BorderStroke(1.dp, Color(0xFFFFC6DB))
        ) {
          Text(
            text = "${currentQuoteIndex.intValue + 1}/${loveQuotes.size}",
            style = TextStyle(
              fontFamily = InLoveFonts.BodySans,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              fontFeatureSettings = "tnum",
              color = Primary,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF5F8),
        border = BorderStroke(1.dp, Color(0xFFFFD1DF)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            imageVector = Icons.Filled.FormatQuote,
            contentDescription = null,
            tint = Primary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          AnimatedContent(
            targetState = loveQuotes[currentQuoteIndex.intValue],
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            label = "quote_transition"
          ) { quoteText ->
            Text(
              text = quoteText,
              style = InLoveTypography.EditorialQuote,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SecondaryButton(
          text = shuffleLabel,
          onClick = {
            currentQuoteIndex.intValue = (currentQuoteIndex.intValue + 1) % loveQuotes.size
            onShuffle()
          },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox),
          icon = Icons.Filled.Refresh,
          testTag = "btn_shuffle_quote"
        )

        Button(
          onClick = onSend,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox)
            .testTag("btn_send_quote")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = sendLabel,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
          )
        }
      }
    }
  }
}
