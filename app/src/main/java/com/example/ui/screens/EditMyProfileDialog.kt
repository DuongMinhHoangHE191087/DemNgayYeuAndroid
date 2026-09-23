package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.OnlineUserEntity
import com.example.ui.util.ProfileUtils

@Composable
private fun profileDialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color(0xFF1E1E24),
  unfocusedTextColor = Color(0xFF1E1E24),
  focusedContainerColor = Color.White,
  unfocusedContainerColor = Color.White,
  focusedBorderColor = Color(0xFFE91E63),
  unfocusedBorderColor = Color(0xFFC7C7CC),
  focusedLabelColor = Color(0xFFE91E63),
  unfocusedLabelColor = Color(0xFF424242),
  focusedPlaceholderColor = Color(0xFF757575),
  unfocusedPlaceholderColor = Color(0xFF9E9E9E),
  cursorColor = Color(0xFFE91E63)
)

@Composable
fun EditMyProfileDialog(
  currentUser: OnlineUserEntity,
  presetAvatars: List<String> = emptyList(),
  isEnglish: Boolean = false,
  onDismiss: () -> Unit,
  onSave: (name: String, birthDate: String, avatarUrl: String, gender: String, bio: String) -> Unit
) {
  var displayName by remember {
    mutableStateOf(if (currentUser.displayName == "Vô danh" && !currentUser.isProfileSetup) "" else currentUser.displayName)
  }
  var birthDate by remember { mutableStateOf(currentUser.birthDate.ifEmpty { "15/10/2004" }) }
  var avatarUrl by remember { mutableStateOf(currentUser.avatarUrl) }
  var gender by remember { mutableStateOf(currentUser.gender) }
  var bio by remember { mutableStateOf(currentUser.bio) }

  // Auto-calculated fields derived in real-time from birth date
  val calculatedAge by remember(birthDate) {
    derivedStateOf { ProfileUtils.calculateAge(birthDate) }
  }
  val calculatedZodiacPair by remember(birthDate) {
    derivedStateOf { ProfileUtils.calculateZodiac(birthDate) }
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp)
        .testTag("edit_my_profile_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Favorite,
              contentDescription = null,
              tint = Color(0xFFE91E63),
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isEnglish) "Personal Profile Setup" else "Thiết Lập Hồ Sơ Cá Nhân",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF880E4F)
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = if (isEnglish) "Close" else "Đóng",
              tint = Color.Gray
            )
          }
        }

        Text(
          text = if (isEnglish) "You can only edit your own profile. Zodiac and age are automatically calculated from your birthday."
                 else "Bạn chỉ có thể chỉnh sửa hồ sơ của chính mình. Cung hoàng đạo và tuổi sẽ tự động tính từ ngày sinh.",
          fontSize = 12.sp,
          color = Color.Gray,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp)
        )

        // Avatar Preview
        Box(
          modifier = Modifier.size(80.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .border(2.5.dp, Color(0xFFFF4081), CircleShape)
          ) {
            AsyncImage(
              model = avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
              contentDescription = "Avatar",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Avatars Row (if available from Cloud)
        if (presetAvatars.isNotEmpty()) {
          Text(
            text = if (isEnglish) "Quick avatar presets:" else "Chọn ảnh đại diện nhanh:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            presetAvatars.forEach { url ->
              val isSelected = avatarUrl == url
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFFE91E63) else Color.LightGray,
                    shape = CircleShape
                  )
                  .clickable { avatarUrl = url }
              ) {
                AsyncImage(
                  model = url,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Name input
        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text(if (isEnglish) "Your Display Name" else "Tên hiển thị của bạn") },
          placeholder = { Text(if (isEnglish) "Leave empty to show 'Anonymous'" else "Để trống sẽ hiển thị 'Vô danh'") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          leadingIcon = {
            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFFE91E63))
          },
          colors = profileDialogTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_my_profile_name")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Gender Selection
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FilterChip(
            selected = gender == "MALE",
            onClick = { gender = "MALE" },
            label = { Text(if (isEnglish) "Male ♂" else "Nam ♂") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Color(0xFFE1F5FE),
              selectedLabelColor = Color(0xFF0288D1)
            ),
            modifier = Modifier.weight(1f)
          )
          FilterChip(
            selected = gender == "FEMALE",
            onClick = { gender = "FEMALE" },
            label = { Text(if (isEnglish) "Female ♀" else "Nữ ♀") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Color(0xFFFCE4EC),
              selectedLabelColor = Color(0xFFC2185B)
            ),
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Birth Date Input
        OutlinedTextField(
          value = birthDate,
          onValueChange = { birthDate = it },
          label = { Text(if (isEnglish) "Birthday (dd/MM/yyyy)" else "Ngày sinh (dd/MM/yyyy)") },
          placeholder = { Text("15/10/2004") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          leadingIcon = {
            Icon(imageVector = Icons.Default.Cake, contentDescription = null, tint = Color(0xFFE91E63))
          },
          colors = profileDialogTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_my_profile_birth")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Real-time Auto-Calculated Age & Zodiac Pill (NON-EDITABLE BY USER)
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFFFFF0F5),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB6C1)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isEnglish) "Auto calculated:" else "Tự động tính:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC2185B)
              )
            }

            Text(
              text = if (isEnglish) "$calculatedAge yrs • Sign ${calculatedZodiacPair.first} ${calculatedZodiacPair.second}"
                     else "$calculatedAge tuổi • Cung ${calculatedZodiacPair.first} ${calculatedZodiacPair.second}",
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF880E4F)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bio input
        OutlinedTextField(
          value = bio,
          onValueChange = { bio = it },
          label = { Text(if (isEnglish) "Loving bio / introduction" else "Lời nhắn nhủ / Giới thiệu") },
          placeholder = { Text(if (isEnglish) "Always loving and by your side 💕" else "Yêu thương và luôn ở bên em 💕") },
          shape = RoundedCornerShape(14.dp),
          maxLines = 3,
          colors = profileDialogTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_my_profile_bio")
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text(if (isEnglish) "Cancel" else "Hủy")
          }

          Button(
            onClick = {
              onSave(
                displayName.trim(),
                birthDate.trim(),
                avatarUrl.trim(),
                gender,
                bio.trim()
              )
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
            modifier = Modifier
              .weight(1f)
              .testTag("btn_save_my_profile")
          ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isEnglish) "Save Profile" else "Lưu hồ sơ", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
