@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BirthdayField
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextSubtlePlum

@Composable
internal fun CoupleDialogTabsRow(
  activeTab: Int,
  isEnglish: Boolean,
  onSelectTab: (Int) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = InLoveColors.RoseTint,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 2.dp)
  ) {
    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      CoupleTabItem(
        selected = activeTab == 0,
        icon = Icons.Default.Favorite,
        label = if (isEnglish) "Profile" else "Hồ sơ",
        onClick = { onSelectTab(0) },
        modifier = Modifier.weight(1f)
      )
      CoupleTabItem(
        selected = activeTab == 1,
        icon = Icons.Default.FavoriteBorder,
        label = if (isEnglish) "Pairing" else "Ghép đôi",
        onClick = { onSelectTab(1) },
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun CoupleTabItem(
  selected: Boolean,
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "tab_bg")
  val color by animateColorAsState(if (selected) Primary else TextSubtlePlum, label = "tab_color")
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(bg)
      .clickable(onClick = onClick)
      .heightIn(min = MinTouchBox),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
      Text(
        text = label,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
        color = color
      )
    }
  }
}

@Composable
internal fun OfflinePartnerProfileCard(
  girlName: String,
  girlBirth: String,
  girlAvatar: String,
  showPhotoPicker: Boolean,
  isEnglish: Boolean,
  onTogglePhotoPicker: () -> Unit,
  onClosePhotoPicker: () -> Unit,
  onNameChange: (String) -> Unit,
  onBirthChange: (String) -> Unit,
  onCameraClick: () -> Unit,
  onGalleryClick: () -> Unit,
  onRemoveAvatar: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    colors = CardDefaults.cardColors(containerColor = InLoveColors.SoftBlushCard),
    border = BorderStroke(1.dp, InLoveColors.BorderSoftPink),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
        Text(
          text = if (isEnglish) "Partner" else "Người ấy",
          fontWeight = FontWeight.Bold,
          fontSize = 13.5.sp,
          color = InLoveColors.RoseHighlight
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          modifier = Modifier
            .size(68.dp)
            .clickable(onClick = onTogglePhotoPicker)
            .testTag("btn_partner_pencil"),
          contentAlignment = Alignment.Center
        ) {
          ProfileAvatar(
            url = girlAvatar,
            contentDescription = "Avatar Đối tác",
            modifier = Modifier
              .size(62.dp)
              .border(2.dp, Primary, CircleShape)
          )
          Surface(
            shape = CircleShape,
            color = Primary,
            shadowElevation = 2.dp,
            modifier = Modifier
              .size(24.dp)
              .align(Alignment.BottomEnd)
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
              Icon(Icons.Default.Edit, contentDescription = "Đổi ảnh", tint = Color.White, modifier = Modifier.size(13.dp))
            }
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          OutlinedTextField(
            value = girlName,
            onValueChange = onNameChange,
            label = { Text(if (isEnglish) "Name" else "Tên") },
            placeholder = { Text(if (isEnglish) "Partner name" else "Tên người ấy") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
            singleLine = true,
            shape = RoundedCornerShape(InLoveRadii.Input),
            colors = inLoveOutlinedTextFieldColors(),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_girl_name")
          )
        }
      }

      AnimatedVisibility(
        visible = showPhotoPicker,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = InLoveColors.RoseTint,
          border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                Text(
                  if (isEnglish) "Partner photo" else "Ảnh người ấy",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = InLoveColors.RoseHighlight
                )
              }
              IconButton(onClick = onClosePhotoPicker, modifier = Modifier.size(MinTouchBox)) {
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSubtlePlum, modifier = Modifier.size(14.dp))
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              SecondaryButton(
                text = if (isEnglish) "Camera" else "Chụp ảnh",
                onClick = onCameraClick,
                icon = Icons.Default.CameraAlt,
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = MinTouchBox),
                testTag = "btn_partner_camera"
              )
              SecondaryButton(
                text = if (isEnglish) "Gallery" else "Thư viện",
                onClick = onGalleryClick,
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = MinTouchBox),
                testTag = "btn_partner_gallery"
              )
              if (girlAvatar.isNotBlank()) {
                SecondaryButton(
                  text = if (isEnglish) "Delete" else "Xoá",
                  onClick = onRemoveAvatar,
                  icon = Icons.Default.DeleteOutline,
                  isDestructive = true,
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchBox),
                  testTag = "btn_partner_remove"
                )
              }
            }
          }
        }
      }

      BirthdayField(
        label = if (isEnglish) "Birthday" else "Ngày sinh",
        value = girlBirth,
        onValueChange = onBirthChange,
        modifier = Modifier.fillMaxWidth(),
        dialogTitle = if (isEnglish) "Select birthday" else "Chọn ngày sinh",
        isEnglish = isEnglish,
        testTag = "input_partner_birth"
      )
    }
  }
}
