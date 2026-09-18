package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.cloudinary.CloudinaryStorageService
import com.example.data.cloudinary.MediaValidationResult
import com.example.data.model.OnlineStatus
import com.example.data.model.SharedMemoryEntity
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoriesGridScreen(
  viewModel: InLoveViewModel,
  modifier: Modifier = Modifier
) {
  val currentLanguage by viewModel.appLanguage.collectAsState()
  val strings = LocalizedStrings.get(currentLanguage)
  val memories by viewModel.sharedMemories.collectAsState()
  val selectedDetail by viewModel.selectedMemoryDetail.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val currentOnlineUser by viewModel.currentOnlineUser.collectAsState()

  var showAddDialog by remember { mutableStateOf(false) }
  var memoryToEdit by remember { mutableStateOf<SharedMemoryEntity?>(null) }
  var selectedFilter by remember { mutableStateOf("all") } // "all", "couple", "mine", "partner", "video", "fav"

  val isCoupled = relationshipStatus == OnlineStatus.COUPLED

  val filteredMemories = remember(memories, selectedFilter, currentOnlineUser) {
    val myUid = currentOnlineUser.uid
    when (selectedFilter) {
      "fav" -> memories.filter { it.isFavorite }
      "couple" -> memories.filter { it.privacyLevel == "COUPLE_ONLY" }
      "mine" -> memories.filter { it.authorId == myUid || it.authorId.isBlank() }
      "partner" -> memories.filter { it.authorId.isNotBlank() && it.authorId != myUid }
      "video" -> memories.filter { it.mediaType == "VIDEO" }
      else -> memories
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (!isCoupled) {
      // Single / Uncoupled State: Require 1-1 pairing to unlock shared memories
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFEBEE)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFE91E63),
            modifier = Modifier.size(52.dp)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "Chưa Kết Nối Người Thương",
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp,
          color = Color(0xFF880E4F),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "Bạn cần kết nối Set Love với người yêu để mở khóa và lưu giữ album kỷ niệm chung 1-1. Tải ảnh & video được bảo vệ an toàn trên Cloudinary và phân quyền rõ ràng giữa 2 bạn.",
          fontSize = 14.sp,
          color = Color.Gray,
          textAlign = TextAlign.Center,
          lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = { viewModel.openPairingScreen() },
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
          modifier = Modifier.height(48.dp).testTag("btn_couple_now_memories")
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Ghép đôi Set Love ngay 💕",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().testTag("memories_grid")
      ) {
        // Header Section with Filters & Cloudinary storage indicator
        item(span = { GridItemSpan(2) }) {
          MemoriesHeader(
            strings = strings,
            totalCount = memories.size,
            selectedFilter = selectedFilter,
            onFilterChanged = { selectedFilter = it },
            onAddClick = { showAddDialog = true }
          )
        }

        // Empty State
        if (filteredMemories.isEmpty()) {
          item(span = { GridItemSpan(2) }) {
            EmptyMemoriesCard(
              strings = strings,
              selectedFilter = selectedFilter,
              onAddClick = { showAddDialog = true }
            )
          }
        } else {
          // Grid items
          items(
            items = filteredMemories,
            key = { it.id }
          ) { memory ->
            MemoryCardItem(
              memory = memory,
              isAuthor = viewModel.isCurrentUserAuthor(memory),
              onClick = { viewModel.openMemoryDetail(memory) },
              onToggleFavorite = { viewModel.toggleMemoryFavorite(memory) }
            )
          }
        }
      }

      // Floating Action Button to Add Photo or Video
      ExtendedFloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = Color(0xFFFF2D75),
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        icon = {
          Icon(
            imageVector = Icons.Default.AddAPhoto,
            contentDescription = strings.btnAddMemory
          )
        },
        text = {
          Text(
            text = strings.btnAddMemory,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
        },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 20.dp, bottom = 24.dp)
          .testTag("btn_fab_add_memory")
      )
    }

    // Add Memory Dialog with Cloudinary & Video support & Permissions
    val dynamicPresetPhotos by viewModel.presetPhotos.collectAsState()
    if (showAddDialog) {
      AddMemoryDialog(
        strings = strings,
        currentLanguage = currentLanguage,
        presetPhotos = dynamicPresetPhotos,
        onDismiss = { showAddDialog = false },
        onSaveMemory = { title, dateText, photoUri, note, location, mediaType, videoUri, cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted, durationSeconds, privacyLevel ->
          viewModel.addSharedMemory(
            title = title,
            dateText = dateText,
            photoUri = photoUri,
            note = note,
            location = location,
            mediaType = mediaType,
            videoUri = videoUri,
            cloudinaryPublicId = cloudinaryPublicId,
            cloudinaryUrl = cloudinaryUrl,
            isCloudinaryStored = isCloudinaryStored,
            fileSizeFormatted = fileSizeFormatted,
            durationSeconds = durationSeconds,
            privacyLevel = privacyLevel
          )
          showAddDialog = false
        }
      )
    }

    // Edit Memory Dialog (Author only)
    if (memoryToEdit != null) {
      EditMemoryDialog(
        memory = memoryToEdit!!,
        onDismiss = { memoryToEdit = null },
        onSave = { updated ->
          viewModel.updateSharedMemory(updated)
          memoryToEdit = null
        }
      )
    }

    // Detail Dialog with full video playback, Cloudinary info & Permissions
    selectedDetail?.let { memory ->
      val isAuthor = viewModel.isCurrentUserAuthor(memory)
      MemoryDetailDialog(
        memory = memory,
        isAuthor = isAuthor,
        strings = strings,
        currentLanguage = currentLanguage,
        onDismiss = { viewModel.closeMemoryDetail() },
        onToggleFavorite = { viewModel.toggleMemoryFavorite(memory) },
        onEdit = {
          viewModel.closeMemoryDetail()
          memoryToEdit = memory
        },
        onDelete = {
          viewModel.deleteSharedMemory(memory.id)
        }
      )
    }
  }
}

@Composable
private fun MemoriesHeader(
  strings: com.example.ui.util.AppStrings,
  totalCount: Int,
  selectedFilter: String,
  onFilterChanged: (String) -> Unit,
  onAddClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 6.dp)
  ) {
    // Glass Banner Header with Cloudinary badge
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White.copy(alpha = 0.92f),
      shadowElevation = 4.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFFFFDDE6), RoundedCornerShape(24.dp))
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.horizontalGradient(
              colors = listOf(
                Color(0xFFFFF0F5),
                Color(0xFFFFE4EC),
                Color(0xFFFFF5F8)
              )
            )
          )
          .padding(18.dp)
      ) {
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.memoryAlbumTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF880E4F)
              )
              Spacer(modifier = Modifier.height(3.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CloudDone,
                  contentDescription = null,
                  tint = Color(0xFF00897B),
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Lưu trữ đám mây Cloudinary • Phân quyền 1-1",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF00695C)
                )
              }
            }

            // Media Count Badge
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFFFF2D75).copy(alpha = 0.12f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF2D75).copy(alpha = 0.3f))
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.PhotoLibrary,
                  contentDescription = null,
                  tint = Color(0xFFFF2D75),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "$totalCount",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = Color(0xFFFF2D75)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Filter Chips Row (Permissions & Categories)
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
          ) {
            FilterChip(
              selected = selectedFilter == "all",
              onClick = { onFilterChanged("all") },
              label = { Text("Tất cả", fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFFF2D75),
                selectedLabelColor = Color.White
              ),
              shape = RoundedCornerShape(20.dp)
            )

            FilterChip(
              selected = selectedFilter == "couple",
              onClick = { onFilterChanged("couple") },
              label = { Text("💑 Chỉ 2 người", fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFE91E63),
                selectedLabelColor = Color.White
              ),
              shape = RoundedCornerShape(20.dp)
            )

            FilterChip(
              selected = selectedFilter == "mine",
              onClick = { onFilterChanged("mine") },
              label = { Text("👤 Của tôi", fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF8E24AA),
                selectedLabelColor = Color.White
              ),
              shape = RoundedCornerShape(20.dp)
            )

            FilterChip(
              selected = selectedFilter == "partner",
              onClick = { onFilterChanged("partner") },
              label = { Text("💕 Của người ấy", fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFD81B60),
                selectedLabelColor = Color.White
              ),
              shape = RoundedCornerShape(20.dp)
            )

            FilterChip(
              selected = selectedFilter == "video",
              onClick = { onFilterChanged("video") },
              label = { Text("🎬 Video", fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF00897B),
                selectedLabelColor = Color.White
              ),
              shape = RoundedCornerShape(20.dp)
            )

            FilterChip(
              selected = selectedFilter == "fav",
              onClick = { onFilterChanged("fav") },
              label = { Text("⭐ Yêu thích", fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFFFB300),
                selectedLabelColor = Color(0xFF3E2723)
              ),
              shape = RoundedCornerShape(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MemoryCardItem(
  memory: SharedMemoryEntity,
  isAuthor: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit
) {
  val isVideo = memory.mediaType == "VIDEO"

  Card(
    shape = RoundedCornerShape(18.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(0.85f)
      .clip(RoundedCornerShape(18.dp))
      .border(1.dp, Color(0xFFFFE0E9), RoundedCornerShape(18.dp))
      .clickable { onClick() }
      .testTag("memory_card_${memory.id}")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Photo / Video Thumbnail
      AsyncImage(
        model = memory.photoUri,
        contentDescription = memory.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Top Shadow Gradient
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
            )
          )
      )

      // Top Badges Row (Cloudinary badge & Video badge & Favorite)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(6.dp)
          .align(Alignment.TopStart),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left badges: Cloud & Video
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // Cloudinary badge
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.5f)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFF80DEEA),
                modifier = Modifier.size(11.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = "Cloud",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }

          if (isVideo) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFF00897B).copy(alpha = 0.85f)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Movie,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(10.dp)
                )
                if (memory.durationSeconds > 0) {
                  Spacer(modifier = Modifier.width(2.dp))
                  Text(
                    text = "${memory.durationSeconds}s",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }
          }
        }

        // Favorite Heart Button
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
          Icon(
            imageVector = if (memory.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (memory.isFavorite) Color(0xFFFF2D75) else Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // Video Play Icon in Center if video
      if (isVideo) {
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play Video",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }
      }

      // Bottom Gradient Scrim for readable title & info
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.6f),
                Color.Black.copy(alpha = 0.92f)
              )
            )
          )
          .padding(horizontal = 10.dp, vertical = 8.dp)
      ) {
        Column {
          Text(
            text = memory.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Spacer(modifier = Modifier.height(3.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFFFFB3C6),
                modifier = Modifier.size(11.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = memory.dateText,
                color = Color(0xFFFFE4EC),
                fontSize = 10.5.sp,
                maxLines = 1
              )
            }

            // Author Badge (Phân quyền)
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = if (isAuthor) Color(0xFF8E24AA).copy(alpha = 0.7f) else Color(0xFFD81B60).copy(alpha = 0.7f)
            ) {
              Text(
                text = if (isAuthor) "Bạn" else memory.authorName,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyMemoriesCard(
  strings: com.example.ui.util.AppStrings,
  selectedFilter: String,
  onAddClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = Color.White.copy(alpha = 0.9f),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 32.dp)
      .border(1.dp, Color(0xFFFFDDE6), RoundedCornerShape(24.dp))
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(28.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(72.dp)
          .background(Color(0xFFFFF0F5), CircleShape)
          .border(2.dp, Color(0xFFFFB3C6), CircleShape)
      ) {
        Icon(
          imageVector = when (selectedFilter) {
            "fav" -> Icons.Default.Favorite
            "video" -> Icons.Default.Movie
            else -> Icons.Default.AddAPhoto
          },
          contentDescription = null,
          tint = Color(0xFFFF2D75),
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = when (selectedFilter) {
          "fav" -> "Chưa có ảnh/video yêu thích nào"
          "video" -> "Chưa có video kỷ niệm nào trên Cloudinary"
          "mine" -> "Bạn chưa đăng kỷ niệm nào"
          "partner" -> "Người ấy chưa đăng kỷ niệm nào"
          else -> strings.memoryEmptyTitle
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF880E4F),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Lưu lại những giây phút ngọt ngào, tải lên Cloudinary an toàn và cùng nhau nhìn lại!",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF6A1B4D).copy(alpha = 0.8f),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(20.dp))

      Button(
        onClick = onAddClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
        shape = RoundedCornerShape(20.dp)
      ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = strings.btnAddMemory, fontWeight = FontWeight.Bold)
      }
    }
  }
}

/**
 * Add Memory Dialog with Photo & Video upload, Cloudinary storage and permissions
 */
@Composable
private fun AddMemoryDialog(
  strings: com.example.ui.util.AppStrings,
  currentLanguage: AppLanguage,
  presetPhotos: List<String> = emptyList(),
  onDismiss: () -> Unit,
  onSaveMemory: (
    title: String,
    dateText: String,
    photoUri: String,
    note: String,
    location: String,
    mediaType: String,
    videoUri: String?,
    cloudinaryPublicId: String?,
    cloudinaryUrl: String?,
    isCloudinaryStored: Boolean,
    fileSizeFormatted: String,
    durationSeconds: Int,
    privacyLevel: String
  ) -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val todayFormatted = remember {
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
  }

  var mediaType by remember { mutableStateOf("IMAGE") } // "IMAGE" or "VIDEO"
  var title by remember { mutableStateOf("") }
  var dateText by remember { mutableStateOf(todayFormatted) }
  var location by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var privacyLevel by remember { mutableStateOf("COUPLE_ONLY") } // "COUPLE_ONLY", "PRIVATE", "PUBLIC"

  var selectedMediaUri by remember(presetPhotos) {
    mutableStateOf(presetPhotos.firstOrNull() ?: "")
  }
  var rawSelectedUri by remember { mutableStateOf<Uri?>(null) }
  var validationResult by remember { mutableStateOf<MediaValidationResult?>(null) }
  var isUploadingToCloudinary by remember { mutableStateOf(false) }

  // System Photo Picker launcher
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    uri?.let {
      val validation = CloudinaryStorageService.validateMedia(context, it, isVideo = false)
      validationResult = validation
      if (validation.isValid) {
        val persistentPath = copyUriToInternalStorage(context, it)
        selectedMediaUri = persistentPath ?: it.toString()
        rawSelectedUri = it
        mediaType = "IMAGE"
      }
    }
  }

  // System Video Picker launcher
  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    uri?.let {
      val validation = CloudinaryStorageService.validateMedia(context, it, isVideo = true)
      validationResult = validation
      if (validation.isValid) {
        selectedMediaUri = it.toString()
        rawSelectedUri = it
        mediaType = "VIDEO"
      }
    }
  }

  // Camera capture launcher (TakePicturePreview)
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    bitmap?.let {
      val savedPath = saveBitmapToInternalStorage(context, it)
      if (savedPath != null) {
        selectedMediaUri = savedPath
        rawSelectedUri = Uri.parse(savedPath)
        mediaType = "IMAGE"
        validationResult = MediaValidationResult(
          isValid = true,
          formattedSize = "Chụp trực tiếp"
        )
      }
    }
  }

  Dialog(onDismissRequest = { if (!isUploadingToCloudinary) onDismiss() }) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = Color.White,
      shadowElevation = 10.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFFFFDDE6), RoundedCornerShape(26.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Title Row
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .background(Color(0xFFFF2D75).copy(alpha = 0.12f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (mediaType == "VIDEO") Icons.Default.Movie else Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = Color(0xFFFF2D75),
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Tải Kỷ Niệm Lên Cloudinary",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF880E4F)
              )
              Text(
                text = "Lưu trữ đám mây • Giới hạn dung lượng & phân quyền",
                fontSize = 11.sp,
                color = Color(0xFF6A1B4D).copy(alpha = 0.75f)
              )
            }
          }

          if (!isUploadingToCloudinary) {
            IconButton(onClick = onDismiss) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Limits Banner (User Request: "upload ảnh hoặc video giới hạn")
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFE0F2F1),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF80CBC4)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = Color(0xFF00796B),
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Giới hạn: Ảnh ≤ 10MB • Video ≤ 60s / 50MB (Lưu Cloudinary)",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF004D40)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Media Preview (Photo or Video indicator)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFFFFC0D3), RoundedCornerShape(18.dp))
            .background(Color(0xFF1E1E24))
        ) {
          if (mediaType == "VIDEO") {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  imageVector = Icons.Default.VideoFile,
                  contentDescription = null,
                  tint = Color(0xFF80DEEA),
                  modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Video đã chọn • Sẵn sàng tải lên Cloudinary",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                if (validationResult != null && validationResult!!.durationSeconds > 0) {
                  Text(
                    text = "Thời lượng: ${validationResult!!.durationSeconds} giây • Dung lượng: ${validationResult!!.formattedSize}",
                    fontSize = 11.sp,
                    color = Color(0xFFB2EBF2)
                  )
                }
              }
            }
          } else {
            AsyncImage(
              model = selectedMediaUri,
              contentDescription = "Selected memory photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }

          // Media Type Badge overlay
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
              Icon(
                imageVector = if (mediaType == "VIDEO") Icons.Default.Movie else Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFF80DEEA),
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (mediaType == "VIDEO") "Video (Max 60s)" else "Ảnh HD Cloudinary",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }

        // Validation Error / Status Feedback
        validationResult?.let { valRes ->
          Spacer(modifier = Modifier.height(8.dp))
          if (!valRes.isValid) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFFEBEE),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = null,
                  tint = Color(0xFFD32F2F),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = valRes.errorMessage ?: "Tập tin không hợp lệ!",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFC62828)
                )
              }
            }
          } else if (valRes.formattedSize.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFE8F5E9),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = Color(0xFF2E7D32),
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Dung lượng: ${valRes.formattedSize} • Đạt tiêu chuẩn Cloudinary ✓",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF1B5E20)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Buttons: Pick Photo, Capture Photo, Pick Video
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("btn_pick_photo")
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Chọn ảnh", fontSize = 11.5.sp)
          }

          OutlinedButton(
            onClick = {
              videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
              )
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00796B)),
            modifier = Modifier.weight(1f).testTag("btn_pick_video")
          ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Chọn video", fontSize = 11.5.sp)
          }

          OutlinedButton(
            onClick = { cameraLauncher.launch(null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("btn_camera_photo")
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Chụp ảnh", fontSize = 11.5.sp)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Thumbnails Row (if photo and presets available)
        if (mediaType == "IMAGE" && presetPhotos.isNotEmpty()) {
          Text(
            text = "Hoặc chọn nhanh ảnh mẫu lãng mạn:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6A1B4D).copy(alpha = 0.8f)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
          ) {
            presetPhotos.forEach { url ->
              val isSelected = selectedMediaUri == url
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFFFF2D75) else Color(0xFFFFE0E9),
                    shape = RoundedCornerShape(12.dp)
                  )
                  .clickable {
                    selectedMediaUri = url
                    rawSelectedUri = null
                    validationResult = MediaValidationResult(isValid = true, formattedSize = "Kho ảnh mẫu")
                  }
              ) {
                AsyncImage(
                  model = url,
                  contentDescription = "Preset photo",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        // Title Input
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Tiêu đề khoảnh khắc") },
          placeholder = { Text("Ví dụ: Hoàng hôn bên bờ biển...") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("input_memory_title"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFF2D75),
            unfocusedBorderColor = Color(0xFFFFDDE6)
          )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Date & Location in a Row
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Ngày kỷ niệm") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("input_memory_date"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFFFF2D75),
              unfocusedBorderColor = Color(0xFFFFDDE6)
            )
          )

          OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Địa điểm") },
            placeholder = { Text("Hà Nội") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("input_memory_location"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFFFF2D75),
              unfocusedBorderColor = Color(0xFFFFDDE6)
            )
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Note Input
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Lời nhắn & cảm xúc yêu thương") },
          placeholder = { Text("Ghi lại cảm xúc ngọt ngào khi ở bên người ấy...") },
          maxLines = 3,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("input_memory_note"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFF2D75),
            unfocusedBorderColor = Color(0xFFFFDDE6)
          )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Phân Quyền (Permissions Section - User Request: "phân quyền cho tôi đầy đủ nhất có thể")
        Text(
          text = "Phân Quyền Xem & Riêng Tư:",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF880E4F)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          // Couple Only
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (privacyLevel == "COUPLE_ONLY") Color(0xFFFCE4EC) else Color(0xFFFAFAFA),
            border = androidx.compose.foundation.BorderStroke(
              width = if (privacyLevel == "COUPLE_ONLY") 1.8.dp else 1.dp,
              color = if (privacyLevel == "COUPLE_ONLY") Color(0xFFE91E63) else Color(0xFFE0E0E0)
            ),
            modifier = Modifier
              .weight(1f)
              .clickable { privacyLevel = "COUPLE_ONLY" }
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = if (privacyLevel == "COUPLE_ONLY") Color(0xFFE91E63) else Color.Gray,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = "Chỉ 2 người",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (privacyLevel == "COUPLE_ONLY") Color(0xFF880E4F) else Color.Gray
              )
            }
          }

          // Private
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (privacyLevel == "PRIVATE") Color(0xFFEDE7F6) else Color(0xFFFAFAFA),
            border = androidx.compose.foundation.BorderStroke(
              width = if (privacyLevel == "PRIVATE") 1.8.dp else 1.dp,
              color = if (privacyLevel == "PRIVATE") Color(0xFF7E57C2) else Color(0xFFE0E0E0)
            ),
            modifier = Modifier
              .weight(1f)
              .clickable { privacyLevel = "PRIVATE" }
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = if (privacyLevel == "PRIVATE") Color(0xFF7E57C2) else Color.Gray,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = "Chỉ mình tôi",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (privacyLevel == "PRIVATE") Color(0xFF4A148C) else Color.Gray
              )
            }
          }

          // Public
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (privacyLevel == "PUBLIC") Color(0xFFE0F2F1) else Color(0xFFFAFAFA),
            border = androidx.compose.foundation.BorderStroke(
              width = if (privacyLevel == "PUBLIC") 1.8.dp else 1.dp,
              color = if (privacyLevel == "PUBLIC") Color(0xFF00897B) else Color(0xFFE0E0E0)
            ),
            modifier = Modifier
              .weight(1f)
              .clickable { privacyLevel = "PUBLIC" }
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = if (privacyLevel == "PUBLIC") Color(0xFF00897B) else Color.Gray,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = "Công khai",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (privacyLevel == "PUBLIC") Color(0xFF004D40) else Color.Gray
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Upload Status Spinner
        if (isUploadingToCloudinary) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color(0xFFFF2D75),
              strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Đang lưu trữ & tải lên Cloudinary... ☁️",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFFE91E63)
            )
          }
        }

        // Action Buttons: Cancel and Save
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedButton(
            onClick = onDismiss,
            enabled = !isUploadingToCloudinary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text(strings.btnCancel)
          }

          val canSave = !isUploadingToCloudinary && (validationResult == null || validationResult!!.isValid)

          Button(
            onClick = {
              isUploadingToCloudinary = true
              coroutineScope.launch {
                val finalTitle = title.ifBlank {
                  if (currentLanguage == AppLanguage.VI) "Khoảnh Khắc Kỷ Niệm" else "Special Moment"
                }

                if (rawSelectedUri != null) {
                  val result = CloudinaryStorageService.uploadToCloudinary(
                    context = context,
                    mediaUri = rawSelectedUri!!,
                    mediaType = mediaType
                  )

                  onSaveMemory(
                    finalTitle,
                    dateText,
                    result.thumbnailUri,
                    note,
                    location,
                    mediaType,
                    if (mediaType == "VIDEO") result.secureUrl else null,
                    result.publicId,
                    result.secureUrl,
                    result.isRealCloudinaryUpload,
                    result.sizeFormatted,
                    result.durationSeconds,
                    privacyLevel
                  )
                } else {
                  // Using selected preset photo
                  onSaveMemory(
                    finalTitle,
                    dateText,
                    selectedMediaUri,
                    note,
                    location,
                    "IMAGE",
                    null,
                    "preset_${System.currentTimeMillis()}",
                    selectedMediaUri,
                    true,
                    "2.1 MB",
                    0,
                    privacyLevel
                  )
                }
              }
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1.2f).testTag("btn_save_memory_submit")
          ) {
            Icon(imageVector = Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Lưu Cloudinary", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

/**
 * Edit Memory Dialog (Author Only)
 */
@Composable
private fun EditMemoryDialog(
  memory: SharedMemoryEntity,
  onDismiss: () -> Unit,
  onSave: (SharedMemoryEntity) -> Unit
) {
  var title by remember { mutableStateOf(memory.title) }
  var dateText by remember { mutableStateOf(memory.dateText) }
  var location by remember { mutableStateOf(memory.location) }
  var note by remember { mutableStateOf(memory.note) }
  var privacyLevel by remember { mutableStateOf(memory.privacyLevel) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      shadowElevation = 10.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFFFFDDE6), RoundedCornerShape(24.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF8E24AA))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Chỉnh Sửa Kỷ Niệm",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF880E4F)
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Tiêu đề") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text("Ngày kỷ niệm") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = location,
          onValueChange = { location = it },
          label = { Text("Địa điểm") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Ghi chú") },
          maxLines = 3,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(text = "Quyền xem & Riêng tư:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF880E4F))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = privacyLevel == "COUPLE_ONLY",
            onClick = { privacyLevel = "COUPLE_ONLY" },
            label = { Text("💑 Chỉ 2 người", fontSize = 11.sp) }
          )
          FilterChip(
            selected = privacyLevel == "PRIVATE",
            onClick = { privacyLevel = "PRIVATE" },
            label = { Text("🔒 Chỉ mình tôi", fontSize = 11.sp) }
          )
          FilterChip(
            selected = privacyLevel == "PUBLIC",
            onClick = { privacyLevel = "PUBLIC" },
            label = { Text("🌐 Công khai", fontSize = 11.sp) }
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Hủy")
          }
          Button(
            onClick = {
              onSave(
                memory.copy(
                  title = title.trim(),
                  dateText = dateText.trim(),
                  location = location.trim(),
                  note = note.trim(),
                  privacyLevel = privacyLevel
                )
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
            modifier = Modifier.weight(1f)
          ) {
            Text("Lưu cập nhật")
          }
        }
      }
    }
  }
}

/**
 * Memory Detail Dialog with Video Player, Cloudinary Card & Role-based Permissions
 */
@Composable
private fun MemoryDetailDialog(
  memory: SharedMemoryEntity,
  isAuthor: Boolean,
  strings: com.example.ui.util.AppStrings,
  currentLanguage: AppLanguage,
  onDismiss: () -> Unit,
  onToggleFavorite: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val context = LocalContext.current
  val isVideo = memory.mediaType == "VIDEO"
  var showDeleteConfirm by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = Color.White,
      shadowElevation = 12.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFFFFDDE6), RoundedCornerShape(26.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Media View: High-Res Photo or Interactive Video View
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFFFE0E9), RoundedCornerShape(20.dp))
            .background(Color.Black)
        ) {
          if (isVideo && !memory.videoUri.isNullOrBlank()) {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  setVideoURI(Uri.parse(memory.videoUri))
                  val mediaController = MediaController(ctx)
                  mediaController.setAnchorView(this)
                  setMediaController(mediaController)
                  setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                  }
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          } else {
            AsyncImage(
              model = memory.photoUri,
              contentDescription = memory.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }

          // Top action buttons (Close & Favorite)
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .padding(10.dp)
          ) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            IconButton(
              onClick = onToggleFavorite,
              modifier = Modifier
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
              Icon(
                imageVector = if (memory.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (memory.isFavorite) Color(0xFFFF2D75) else Color.White
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title Row
        Text(
          text = memory.title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF880E4F)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Date, Location & Media Badges
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFFF0F5),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDDE6))
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFFFF2D75),
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = memory.dateText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF880E4F)
              )
            }
          }

          if (memory.location.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFFF8E1),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE082))
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.LocationOn,
                  contentDescription = null,
                  tint = Color(0xFFF57C00),
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = memory.location,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFFE65100)
                )
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFE0F2F1),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF80CBC4))
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(
                imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFF00796B),
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isVideo) "Video ${memory.durationSeconds}s" else "Ảnh Cloudinary",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF004D40)
              )
            }
          }
        }

        // Note
        if (memory.note.isNotBlank()) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFFF7FA),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE4EC)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "“${memory.note}”",
              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF5D1049),
              lineHeight = 20.sp,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Cloudinary Storage Information Card (User Request: "lưu trữ dữ liệu này bởi cloudinary")
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFFF1F8E9),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC5E1A5)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CloudDone,
                  contentDescription = null,
                  tint = Color(0xFF33691E),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Lưu Trữ Cloudinary An Toàn",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF33691E)
                )
              }

              if (!memory.cloudinaryUrl.isNullOrBlank()) {
                IconButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    @Suppress("UsePropertyAccessSyntax")
                    clipboard.setPrimaryClip(ClipData.newPlainText("Cloudinary URL", memory.cloudinaryUrl))
                  },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Link",
                    tint = Color(0xFF33691E),
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "Public ID: ${memory.cloudinaryPublicId ?: "inlove_asset"}",
              fontSize = 11.sp,
              color = Color(0xFF558B2F)
            )
            if (memory.fileSizeFormatted.isNotBlank()) {
              Text(
                text = "Dung lượng: ${memory.fileSizeFormatted}",
                fontSize = 11.sp,
                color = Color(0xFF558B2F)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Permissions & Role Notice (User Request: "phân quyền cho tôi đầy đủ nhất có thể")
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isAuthor) Color(0xFFEDE7F6) else Color(0xFFFFF3E0),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAuthor) Color(0xFFB39DDB) else Color(0xFFFFCC80)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
          ) {
            Icon(
              imageVector = if (isAuthor) Icons.Default.Person else Icons.Default.Lock,
              contentDescription = null,
              tint = if (isAuthor) Color(0xFF512DA8) else Color(0xFFE65100),
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isAuthor) {
                "Bạn là tác giả của kỷ niệm này • Có toàn quyền chỉnh sửa và xóa."
              } else {
                "Tác giả: ${memory.authorName} • Bạn có quyền xem và thả tim yêu thích."
              },
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = if (isAuthor) Color(0xFF311B92) else Color(0xFFBF360C)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions: If Author -> Edit & Delete. If Partner -> Read Only
        if (showDeleteConfirm) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            OutlinedButton(
              onClick = { showDeleteConfirm = false },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text(strings.btnCancel)
            }

            Button(
              onClick = {
                onDelete()
                onDismiss()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Xác nhận xóa")
            }
          }
        } else {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            if (isAuthor) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = { showDeleteConfirm = true },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                  Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Xóa")
                }

                Button(
                  onClick = onEdit,
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))
                ) {
                  Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Sửa")
                }
              }
            } else {
              // Partner view: only Got It button
              Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
              onClick = onDismiss,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75))
            ) {
              Text(strings.btnGotIt)
            }
          }
        }
      }
    }
  }
}

// Helpers for persisting media safely
fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
  return try {
    val filename = "shared_mem_${System.currentTimeMillis()}.jpg"
    val file = File(context.filesDir, filename)
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    Uri.fromFile(file).toString()
  } catch (e: Exception) {
    null
  }
}

fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
  return try {
    val filename = "picked_mem_${System.currentTimeMillis()}.jpg"
    val destFile = File(context.filesDir, filename)
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(destFile).use { output ->
        input.copyTo(output)
      }
    }
    Uri.fromFile(destFile).toString()
  } catch (e: Exception) {
    null
  }
}
