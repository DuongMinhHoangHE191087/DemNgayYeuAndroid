@file:Suppress("FunctionName")
package com.example.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.ui.components.PartnerCard
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.CardBorder
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.ProfileUtils
import com.example.ui.util.displayPartnerName

/**
 * Dual partner showcase card with zero-recomposition graphicsLayer heartbeat ripples.
 */
@Composable
fun HomeCoupleProfileCard(
  currentOnlineUser: OnlineUserEntity,
  partnerUser: OnlineUserEntity?,
  relationshipStatus: String?,
  boyName: String,
  boyBirthDate: String,
  boyAvatarUrl: String,
  boyAge: Int,
  boyZodiac: String,
  girlName: String,
  girlBirthDate: String,
  girlAvatarUrl: String,
  girlAge: Int,
  girlZodiac: String,
  meLabel: String,
  partnerLabel: String,
  isEnglish: Boolean,
  avatarOuterSize: Dp,
  avatarInnerSize: Dp,
  centerHeartSize: Dp,
  onMyAvatarClick: () -> Unit,
  onHeartClick: (isCoupled: Boolean) -> Unit,
  onCoupledPartnerClick: () -> Unit,
  onOfflinePartnerClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "heart_pulse")
  val pulseScale: State<Float> = infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.18f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "heart_scale"
  )
  val ripple1Scale: State<Float> = infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.75f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "ripple1_scale"
  )
  val ripple1Alpha: State<Float> = infiniteTransition.animateFloat(
    initialValue = 0.55f,
    targetValue = 0.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "ripple1_alpha"
  )
  val ripple2Scale: State<Float> = infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.50f,
    animationSpec = infiniteRepeatable(
      animation = tween(1800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "ripple2_scale"
  )
  val ripple2Alpha: State<Float> = infiniteTransition.animateFloat(
    initialValue = 0.45f,
    targetValue = 0.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "ripple2_alpha"
  )

  Card(
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
    border = BorderStroke(1.2.dp, CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("couple_bottom_section")
  ) {
    val isCoupled = relationshipStatus == OnlineStatus.COUPLED && partnerUser != null

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .testTag("current_user_profile_col")
      ) {
        Box(
          modifier = Modifier
            .size(avatarOuterSize)
            .clip(CircleShape)
            .clickable(
              onClickLabel = if (isEnglish) "Edit profile" else "Chỉnh sửa hồ sơ",
              role = Role.Button,
              onClick = onMyAvatarClick
            )
            .testTag("home_my_avatar"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(avatarInnerSize)
              .clip(CircleShape)
              .border(2.dp, Color(0xFF81D4FA), CircleShape)
              .shadow(3.dp, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            ProfileAvatar(
              url = currentOnlineUser.avatarUrl.ifEmpty { boyAvatarUrl },
              contentDescription = currentOnlineUser.effectiveDisplayName,
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val displayedUserName = currentOnlineUser.effectiveDisplayName.ifBlank { meLabel }
        Text(
          text = displayedUserName,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(3.dp))

        val myBirth = currentOnlineUser.birthDate.ifBlank { boyBirthDate }
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (!currentOnlineUser.isProfileSetup && boyName.isBlank()) {
            Surface(
              shape = RoundedCornerShape(50.dp),
              color = Color(0xFFFFF0F5),
              border = BorderStroke(1.dp, CardBorder)
            ) {
              Text(
                text = if (isEnglish) "Not set" else "Chưa đặt tên",
                color = TextSubtlePlum,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          } else {
            val myAge = ProfileUtils.calculateAge(myBirth).takeIf { it > 0 }
              ?: (if (currentOnlineUser.age > 0) currentOnlineUser.age else boyAge)
            if (myAge > 0) {
              Surface(shape = RoundedCornerShape(50.dp), color = Color(0xFFE0F7FA)) {
                Text(
                  text = "${myAge}t",
                  color = Color(0xFF00838F),
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
            val myZodiac = ProfileUtils.formatZodiacDisplay(
              currentOnlineUser.zodiac.ifEmpty { boyZodiac },
              isEnglish
            )
            if (myZodiac.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color(0xFFF3E5F5),
                modifier = Modifier.weight(1f, fill = false)
              ) {
                Text(
                  text = myZodiac,
                  color = Color(0xFF7B1FA2),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
        }

        if (myBirth.isNotBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = myBirth,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSubtlePlum
          )
        }
      }

      Box(
        modifier = Modifier.size(avatarOuterSize),
        contentAlignment = Alignment.Center
      ) {
        // Read animated wave states inside graphicsLayer draw phase for smooth 60fps.
        Box(
          modifier = Modifier
            .size(centerHeartSize)
            .graphicsLayer {
              val s = ripple1Scale.value
              scaleX = s
              scaleY = s
              alpha = ripple1Alpha.value
            }
            .clip(CircleShape)
            .background(Color(0xFFFF80AB))
        )
        Box(
          modifier = Modifier
            .size(centerHeartSize)
            .graphicsLayer {
              val s = ripple2Scale.value
              scaleX = s
              scaleY = s
              alpha = ripple2Alpha.value
            }
            .clip(CircleShape)
            .background(Color(0xFFFFB6C1))
        )
        Box(
          modifier = Modifier
            .graphicsLayer {
              val s = pulseScale.value
              scaleX = s
              scaleY = s
            }
            .size(centerHeartSize)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(RoseGradientStart, RoseGradientMid)))
            .clickable { onHeartClick(isCoupled) }
            .testTag("btn_center_heart"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = if (isEnglish) "Love heart" else "Trái tim tình yêu",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      if (isCoupled && partnerUser != null) {
        val partnerBirth = partnerUser.birthDate.ifEmpty { girlBirthDate }
        PartnerCard(
          avatarUrl = partnerUser.avatarUrl.ifEmpty { girlAvatarUrl },
          name = displayPartnerName(partnerUser.effectiveDisplayName.ifBlank { girlName }, isEnglish),
          age = ProfileUtils.calculateAge(partnerBirth).takeIf { it > 0 }
            ?: (if (partnerUser.age > 0) partnerUser.age else girlAge),
          zodiac = ProfileUtils.formatZodiacDisplay(partnerUser.zodiac.ifEmpty { girlZodiac }, isEnglish),
          birthDate = partnerBirth,
          showHeartBadge = true,
          avatarOuterSize = avatarOuterSize,
          avatarInnerSize = avatarInnerSize,
          onClick = onCoupledPartnerClick,
          testTag = "partner_user_profile_col",
          modifier = Modifier.weight(1f)
        )
      } else {
        PartnerCard(
          avatarUrl = girlAvatarUrl,
          name = partnerLabel,
          age = ProfileUtils.calculateAge(girlBirthDate).takeIf { it > 0 } ?: girlAge,
          zodiac = ProfileUtils.formatZodiacDisplay(girlZodiac, isEnglish),
          birthDate = girlBirthDate,
          showHeartBadge = false,
          avatarOuterSize = avatarOuterSize,
          avatarInnerSize = avatarInnerSize,
          onClick = onOfflinePartnerClick,
          testTag = "partner_offline_profile_col",
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}
