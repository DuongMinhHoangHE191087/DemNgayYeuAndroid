package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.widthIn
import com.example.ui.components.animateDialogEntrance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.OnlineUserEntity
import com.example.ui.components.pressClickable
import com.example.ui.theme.MinTouchBox
import com.example.ui.util.ProfileUtils
import java.io.File

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
  canEditPhoto: Boolean = true,
  onPhotoPicked: (Uri) -> String = { "" },
  onDismiss: () -> Unit,
  onSave: (name: String, birthDate: String, avatarUrl: String, gender: String, bio: String) -> Unit,
  onPhotoDiscarded: (String) -> Unit = {}
) {
  Dialog(onDismissRequest = onDismiss) {
    com.example.ui.theme.AppDensity {
      EditMyProfileDialogContent(currentUser, presetAvatars, isEnglish, canEditPhoto, onPhotoPicked, onDismiss, onSave, onPhotoDiscarded)
    }
  }
}

@Composable
private fun ProfilePhotoActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tag: String,
  isDestructive: Boolean = false,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (isDestructive) Color(0xFFFFEBEE) else Color(0xFFFFF0F5),
    border = BorderStroke(1.dp, if (isDestructive) Color(0xFFFFCDD2) else Color(0xFFFFB6C1)),
    modifier = Modifier
      .heightIn(min = 38.dp)
      .clip(RoundedCornerShape(20.dp))
      .pressClickable(onClick = onClick)
      .testTag(tag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isDestructive) Color(0xFFD32F2F) else Color(0xFFE91E63),
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (isDestructive) Color(0xFFD32F2F) else Color(0xFFE91E63)
      )
    }
  }
}

// Split from the Dialog window so tests can compose it directly: Robolectric never goes idle
// with an OutlinedTextField inside a Dialog window.
@Composable
internal fun EditMyProfileDialogContent(
  currentUser: OnlineUserEntity,
  presetAvatars: List<String> = emptyList(),
  isEnglish: Boolean = false,
  canEditPhoto: Boolean = true,
  onPhotoPicked: (Uri) -> String = { "" },
  onDismiss: () -> Unit,
  onSave: (name: String, birthDate: String, avatarUrl: String, gender: String, bio: String) -> Unit,
  onPhotoDiscarded: (String) -> Unit = {}
) {
  var displayName by remember {
    mutableStateOf(if (currentUser.displayName == "Vô danh" && !currentUser.isProfileSetup) "" else currentUser.displayName)
  }
  var birthDate by remember { mutableStateOf(currentUser.birthDate.ifEmpty { "15/10/2004" }) }
  var avatarUrl by remember { mutableStateOf(currentUser.avatarUrl) }
  var gender by remember { mutableStateOf(currentUser.gender) }
  var bio by remember { mutableStateOf(currentUser.bio) }

  val context = LocalContext.current
  // Copies made this session. On close, each one except the submitted avatar is deleted.
  val pickedCopies = remember { mutableListOf<String>() }
  var submittedUrl by remember { mutableStateOf("") }
  DisposableEffect(Unit) {
    onDispose {
      // ponytail: a Save that fails and then closes keeps its copy. Upgrade: onSave reports success, so a failed save resets submittedUrl.
      pickedCopies.filter { it != submittedUrl }.forEach(onPhotoDiscarded)
    }
  }
  // "" from onPhotoPicked means the copy failed: keep the current avatar
  fun applyPick(url: String) {
    if (url.isEmpty()) return
    pickedCopies += url
    avatarUrl = url
  }
  val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) applyPick(onPhotoPicked(uri))
  }
  val captureFile = remember { File(context.cacheDir, "camera/avatar_capture.jpg") }
  val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
    if (saved) applyPick(onPhotoPicked(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)))
  }

  // Auto-calculated fields derived in real-time from birth date
  val calculatedAge by remember(birthDate) {
    derivedStateOf { ProfileUtils.calculateAge(birthDate) }
  }
  val calculatedZodiacPair by remember(birthDate) {
    derivedStateOf { ProfileUtils.calculateZodiac(birthDate) }
  }

  var showPhotoActionPicker by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    modifier = Modifier
      .widthIn(max = 380.dp)
      .fillMaxWidth(0.92f)
      .animateDialogEntrance()
      .padding(vertical = 12.dp)
      .testTag("edit_my_profile_dialog")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(18.dp),
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
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isEnglish) "My profile" else "Hồ sơ của tôi",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF880E4F)
          )
        }

        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(MinTouchBox)
            .clip(CircleShape)
            .pressClickable(onClick = onDismiss)
            .testTag("btn_close_edit_profile")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = if (isEnglish) "Close" else "Đóng",
            tint = Color.Gray
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Avatar Preview with Single Pencil Edit Badge
      Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(
              BorderStroke(
                2.5.dp,
                Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFE91E63)))
              ),
              CircleShape
            )
            .clickable {
              if (canEditPhoto) showPhotoActionPicker = !showPhotoActionPicker
            }
        ) {
          AsyncImage(
            model = avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
            contentDescription = if (isEnglish) "Profile photo" else "Ảnh đại diện",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Cute pencil button at bottom-end corner
        if (canEditPhoto) {
          Surface(
            shape = CircleShape,
            color = Color(0xFFE91E63),
            shadowElevation = 3.dp,
            modifier = Modifier
              .size(28.dp)
              .align(Alignment.BottomEnd)
              .clickable { showPhotoActionPicker = !showPhotoActionPicker }
              .testTag("btn_photo_pencil")
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = if (isEnglish) "Edit avatar" else "Chỉnh sửa ảnh đại diện",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
              )
            }
          }
        }
      }

      // Animated Photo Action Panel (Appears only when pencil button is clicked)
      AnimatedVisibility(
        visible = showPhotoActionPicker && canEditPhoto,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFFF0F5),
          border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(15.dp))
                Text(
                  text = if (isEnglish) "Edit avatar" else "Thao tác ảnh",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF880E4F)
                )
              }
              IconButton(
                onClick = { showPhotoActionPicker = false },
                modifier = Modifier.size(24.dp).testTag("btn_photo_cancel")
              ) {
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray, modifier = Modifier.size(15.dp))
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Button(
                onClick = {
                  showPhotoActionPicker = false
                  captureFile.parentFile?.mkdirs()
                  takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFE91E63)),
                border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("btn_photo_camera")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                  Text(if (isEnglish) "Camera" else "Chụp ảnh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
              }

              Button(
                onClick = {
                  showPhotoActionPicker = false
                  pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFE91E63)),
                border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("btn_photo_library")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                  Text(if (isEnglish) "Gallery" else "Thư viện", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
              }

              if (avatarUrl.isNotBlank()) {
                Button(
                  onClick = {
                    showPhotoActionPicker = false
                    avatarUrl = ""
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFD32F2F)),
                  border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).height(40.dp).testTag("btn_photo_remove")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(if (isEnglish) "Delete" else "Xoá", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                  }
                }
              }
            }
          }
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
          presetAvatars.forEachIndexed { index, url ->
            val isSelected = avatarUrl == url
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(MinTouchBox)
                .clip(CircleShape)
                .pressClickable(onClick = { avatarUrl = url })
                .testTag("preset_avatar_$index")
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFFE91E63) else Color.LightGray,
                    shape = CircleShape
                  )
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
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Name input
      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text(if (isEnglish) "Name" else "Tên") },
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
        listOf(
          Triple("MALE", Icons.Filled.Male, Color(0xFF0288D1)),
          Triple("FEMALE", Icons.Filled.Female, Color(0xFFC2185B))
        ).forEach { (key, icon, tint) ->
          val selected = gender == key
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .weight(1f)
              .height(MinTouchBox)
              .clip(RoundedCornerShape(14.dp))
              .background(if (selected) tint.copy(alpha = 0.14f) else Color(0xFFF5F5F5))
              .border(if (selected) 1.5.dp else 0.dp, tint, RoundedCornerShape(14.dp))
              .pressClickable(onClick = { gender = key })
          ) {
            Icon(
              imageVector = icon,
              contentDescription = if (key == "MALE") (if (isEnglish) "Male" else "Nam") else (if (isEnglish) "Female" else "Nữ"),
              tint = if (selected) tint else Color.Gray,
              modifier = Modifier.size(24.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Birth Date Input
      OutlinedTextField(
        value = birthDate,
        onValueChange = { birthDate = it },
        label = { Text(if (isEnglish) "Birthday" else "Ngày sinh") },
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
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFE91E63),
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "${ProfileUtils.formatAgeDisplay(calculatedAge, isEnglish)} • ${ProfileUtils.formatZodiacDisplay(calculatedZodiacPair.first, isEnglish)}",
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
        label = { Text(if (isEnglish) "Bio" else "Giới thiệu") },
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
            submittedUrl = avatarUrl.trim()
            onSave(
              displayName.trim(),
              birthDate.trim(),
              submittedUrl,
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
          Text(if (isEnglish) "Save" else "Lưu", fontWeight = FontWeight.Bold, maxLines = 1)
        }
      }
    }
  }
}
