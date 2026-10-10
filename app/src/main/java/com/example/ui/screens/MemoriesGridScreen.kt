@file:Suppress("FunctionName")
package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.key
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
import com.example.data.media.LocalMediaStore
import com.example.data.model.OnlineStatus
import com.example.data.model.PRIVACY_PRIVATE
import com.example.data.model.SharedMemoryEntity
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.formatMediaSize
import com.example.ui.components.rememberMemoryUri
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  val isEnglish = currentLanguage == com.example.ui.util.AppLanguage.EN

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
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.fillMaxSize().testTag("memories_grid")
    ) {
      // Sleek Header Section with Hero Card & Horizontal Category Pills
      item(span = { GridItemSpan(2) }) {
        MemoriesHeader(
          strings = strings,
          totalCount = memories.size,
          selectedFilter = selectedFilter,
          onFilterChanged = { selectedFilter = it },
          onAddClick = { showAddDialog = true },
          isCoupled = isCoupled,
          isEnglish = isEnglish,
          onOpenPairing = { viewModel.openPairingScreen() }
        )
      }

      // Empty State
      if (filteredMemories.isEmpty()) {
        item(span = { GridItemSpan(2) }) {
          EmptyMemoriesCard(
            strings = strings,
            selectedFilter = selectedFilter,
            onAddClick = { showAddDialog = true },
            isEnglish = isEnglish
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
            isEnglish = isEnglish,
            onClick = { viewModel.openMemoryDetail(memory) },
            onToggleFavorite = { viewModel.toggleMemoryFavorite(memory) },
            resolveMedia = { viewModel.resolveMemoryMediaUrl(memory) }
          )
        }
      }
    }

    // Modern Floating Action Button with subtle shadow and heart accent
    ExtendedFloatingActionButton(
      onClick = { showAddDialog = true },
      containerColor = Color(0xFFFF2D75),
      contentColor = Color.White,
      shape = RoundedCornerShape(28.dp),
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.AddAPhoto,
          contentDescription = strings.btnAddMemory
        )
      },
      text = {
        Text(
          text = if (isEnglish) "Add Memory" else strings.btnAddMemory,
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

  // Add Memory Dialog: photo or video, uploaded and confirmed by the server before it is saved
  val dynamicPresetPhotos by viewModel.presetPhotos.collectAsState()
  if (showAddDialog) {
    AddMemoryDialog(
      strings = strings,
      currentLanguage = currentLanguage,
      presetPhotos = dynamicPresetPhotos,
      onDismiss = { showAddDialog = false },
      onSaveMemory = { title, dateText, photoUri, note, location, media, coverUri, privacyLevel ->
        viewModel.saveSharedMemory(title, dateText, photoUri, note, location, media, coverUri, privacyLevel)
      }
    )
  }

  // Edit Memory Dialog (Author only)
  memoryToEdit?.let { editing ->
    EditMemoryDialog(
      memory = editing,
      isEnglish = isEnglish,
      onDismiss = { memoryToEdit = null },
      onSave = { updated ->
        viewModel.updateSharedMemory(updated)
        memoryToEdit = null
      }
    )
  }

  // Detail Dialog with full video playback, storage status & permissions
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
      },
      resolveMedia = { viewModel.resolveMemoryMediaUrl(memory) }
    )
  }
}

@Composable
private fun MemoriesHeader(
  strings: com.example.ui.util.AppStrings,
  totalCount: Int,
  selectedFilter: String,
  onFilterChanged: (String) -> Unit,
  onAddClick: () -> Unit,
  isCoupled: Boolean = false,
  isEnglish: Boolean = false,
  onOpenPairing: () -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 6.dp)
  ) {
    // 1. Sleek Modern Romantic Hero Card
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 3.dp,
      border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFD1DC)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFFFF80AB), Color(0xFFFF2D75))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.PhotoLibrary,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = if (isEnglish) "Memories" else "Kho Kỷ Niệm",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF880E4F)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color(0xFFFFF0F5),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB3C6))
              ) {
                Text(
                  text = "$totalCount",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFE91E63),
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
              text = if (isCoupled) {
                if (isEnglish) "Cloud Sync • Real-Time Couple" else "Đồng bộ đám mây • Ghép đôi 1-1"
              } else {
                if (isEnglish) "Local Storage • Device Only" else "Lưu trữ máy • Ngoại tuyến"
              },
              fontSize = 11.5.sp,
              color = if (isCoupled) Color(0xFF00897B) else Color(0xFF7E57C2),
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }

    // 2. Offline Pairing Prompt Banner (If single/offline)
    if (!isCoupled) {
      Spacer(modifier = Modifier.height(10.dp))
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF0F5),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
        shadowElevation = 1.dp,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpenPairing() }
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.Favorite,
              contentDescription = null,
              tint = Color(0xFFE91E63),
              modifier = Modifier.size(17.dp)
            )
            Text(
              text = if (isEnglish) "Offline Mode: Stored on device. Tap to Pair 1-1."
                     else "Chế độ Ngoại Tuyến: Kỷ niệm lưu trên máy. Chạm để Ghép đôi 1-1.",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFFC2185B)
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color(0xFFE91E63),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 3. Filter Chips Row (Permissions & Categories)
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
    ) {
      FilterChip(
        selected = selectedFilter == "all",
        onClick = { onFilterChanged("all") },
        label = { Text(if (isEnglish) "✨ All" else "✨ Tất cả", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Color(0xFFFF2D75),
          selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
      )

      FilterChip(
        selected = selectedFilter == "couple",
        onClick = { onFilterChanged("couple") },
        label = { Text(if (isEnglish) "💑 Couple Only" else "💑 Chỉ 2 người", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Color(0xFFE91E63),
          selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
      )

      FilterChip(
        selected = selectedFilter == "mine",
        onClick = { onFilterChanged("mine") },
        label = { Text(if (isEnglish) "👤 Mine" else "👤 Của tôi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Color(0xFF8E24AA),
          selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
      )

      FilterChip(
        selected = selectedFilter == "partner",
        onClick = { onFilterChanged("partner") },
        label = { Text(if (isEnglish) "💕 Partner" else "💕 Của người ấy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Color(0xFFD81B60),
          selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
      )

      FilterChip(
        selected = selectedFilter == "video",
        onClick = { onFilterChanged("video") },
        label = { Text(if (isEnglish) "🎬 Video" else "🎬 Video", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Color(0xFF00897B),
          selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
      )

      FilterChip(
        selected = selectedFilter == "fav",
        onClick = { onFilterChanged("fav") },
        label = { Text(if (isEnglish) "⭐ Favorites" else "⭐ Yêu thích", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Color(0xFFFFB300),
          selectedLabelColor = Color(0xFF3E2723)
        ),
        shape = RoundedCornerShape(20.dp)
      )
    }
  }
}

@Composable
private fun MemoryCardItem(
  memory: SharedMemoryEntity,
  isAuthor: Boolean,
  isEnglish: Boolean = false,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  resolveMedia: suspend () -> String?
) {
  val isVideo = memory.mediaType == "VIDEO"
  val hasCloud = !memory.cloudinaryPublicId.isNullOrBlank()
  // Local copy first; a partner's device has only the publicId, so it resolves a short-lived URL.
  val photoModel = rememberMemoryUri(
    memoryId = memory.syncId,
    localUri = memory.photoUri,
    cloudPublicId = memory.cloudinaryPublicId.takeIf { !isVideo },
    resolve = resolveMedia
  )

  Card(
    shape = RoundedCornerShape(20.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(0.82f)
      .clip(RoundedCornerShape(20.dp))
      .border(1.2.dp, Color(0xFFFFE0E9), RoundedCornerShape(20.dp))
      .clickable { onClick() }
      .testTag("memory_card_${memory.id}")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Photo / Video Thumbnail
      AsyncImage(
        model = photoModel,
        contentDescription = memory.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Top Shadow Gradient for readable badges
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
            )
          )
      )

      // Top Badges Row (Cloud/Local badge & Video badge & Favorite)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp)
          .align(Alignment.TopStart),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left badges: Storage & Video
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // "Cloud" only once the server confirmed the upload (publicId set); a local-only memory never claims it.
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.55f)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
              Icon(
                imageVector = if (hasCloud) Icons.Default.Cloud else Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = if (hasCloud) Color(0xFF80DEEA) else Color(0xFFFFB74D),
                modifier = Modifier.size(11.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = when {
                  hasCloud -> if (isEnglish) "Cloud" else "Đám mây"
                  else -> if (isEnglish) "On device" else "Trên máy"
                },
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }

          if (isVideo) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF00897B).copy(alpha = 0.88f)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Movie,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(10.dp)
                )
                if (memory.durationSeconds > 0) {
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "${memory.durationSeconds}s",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }
          }
        }

        // Favorite Heart Button with glassmorphism ring
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.38f), CircleShape)
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
            .size(46.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play Video",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
          )
        }
      }

      // Bottom Gradient Scrim for crystal clear text info
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.65f),
                Color.Black.copy(alpha = 0.94f)
              )
            )
          )
          .padding(horizontal = 10.dp, vertical = 9.dp)
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

            // Author Badge (Role & Permission)
            Surface(
              shape = RoundedCornerShape(5.dp),
              color = if (isAuthor) Color(0xFF8E24AA).copy(alpha = 0.75f) else Color(0xFFD81B60).copy(alpha = 0.75f)
            ) {
              Text(
                text = if (isAuthor) (if (isEnglish) "You" else "Bạn") else memory.authorName.ifBlank { if (isEnglish) "Partner" else "Người ấy" },
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
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
  onAddClick: () -> Unit,
  isEnglish: Boolean = false
) {
  Surface(
    shape = RoundedCornerShape(26.dp),
    color = Color.White.copy(alpha = 0.98f),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 24.dp)
      .border(1.2.dp, Color(0xFFFFDDE6), RoundedCornerShape(26.dp)),
    shadowElevation = 2.dp
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(26.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(76.dp)
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
          modifier = Modifier.size(38.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = when (selectedFilter) {
          "fav" -> if (isEnglish) "No favorite memories yet" else "Chưa có ảnh/video yêu thích nào"
          "video" -> if (isEnglish) "No memory videos yet" else "Chưa có video kỷ niệm nào"
          "mine" -> if (isEnglish) "You haven't posted any memories yet" else "Bạn chưa đăng kỷ niệm nào"
          "partner" -> if (isEnglish) "Partner hasn't posted any memories yet" else "Người ấy chưa đăng kỷ niệm nào"
          else -> strings.memoryEmptyTitle
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF880E4F),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = if (isEnglish) "Capture your sweet moments, keep them safe and cherish them forever!"
               else "Lưu lại những giây phút ngọt ngào, lưu trữ an toàn và cùng nhau nhìn lại!",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF6A1B4D).copy(alpha = 0.8f),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(18.dp))

      Button(
        onClick = onAddClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
      ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isEnglish) "Add New Memory" else strings.btnAddMemory,
          fontWeight = FontWeight.Bold,
          fontSize = 13.5.sp
        )
      }
    }
  }
}

/**
 * Add Memory Dialog: pick a photo or video, then save. [onSaveMemory] returns true only once the upload
 * is confirmed and the memory is stored; the dialog closes itself on true and stays open on false.
 */
@Composable
fun AddMemoryDialog(
  strings: com.example.ui.util.AppStrings,
  currentLanguage: AppLanguage,
  presetPhotos: List<String> = emptyList(),
  onDismiss: () -> Unit,
  onSaveMemory: suspend (
    title: String,
    dateText: String,
    photoUri: String,
    note: String,
    location: String,
    media: MemoryMediaFile?,
    coverUri: String?,
    privacyLevel: String
  ) -> Boolean
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val todayFormatted = remember {
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
  }

  var title by remember { mutableStateOf("") }
  var dateText by remember { mutableStateOf(todayFormatted) }
  var location by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var privacyLevel by remember { mutableStateOf("COUPLE_ONLY") } // "COUPLE_ONLY" or "PRIVATE"

  var selectedMediaUri by remember(presetPhotos) {
    mutableStateOf(presetPhotos.firstOrNull() ?: "")
  }
  // The picked file is the single source of truth for the media type; presets leave it null.
  var pickedMedia by remember { mutableStateOf<MemoryMediaFile?>(null) }
  var validationError by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }
  val isVideo = pickedMedia?.kind == MediaKind.VIDEO

  // System photo/video picker: copy and validate off the main thread, then show the result
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    uri?.let { picked ->
      coroutineScope.launch {
        val pick = withContext(Dispatchers.IO) { LocalMediaStore.prepare(context, picked) }
        validationError = pick.error
        if (pick.media != null) {
          pickedMedia = pick.media
          selectedMediaUri = pick.coverUri.orEmpty()
        }
      }
    }
  }

  Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
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
                imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = Color(0xFFFF2D75),
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = if (currentLanguage == AppLanguage.EN) "Save Sweet Memory" else "Lưu Trữ Kỷ Niệm",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF880E4F)
              )
              Text(
                text = if (currentLanguage == AppLanguage.EN) "Cherish unforgettable moments together" else "Lưu giữ an toàn từng khoảnh khắc đáng nhớ",
                fontSize = 11.sp,
                color = Color(0xFF6A1B4D).copy(alpha = 0.75f)
              )
            }
          }

          if (!isSaving) {
            IconButton(onClick = onDismiss) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selected Media Preview (Photo or Video indicator)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFFFFC0D3), RoundedCornerShape(18.dp))
            .background(Color(0xFF1E1E24))
        ) {
          if (isVideo) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  imageVector = Icons.Default.VideoFile,
                  contentDescription = null,
                  tint = Color(0xFF80DEEA),
                  modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = if (currentLanguage == AppLanguage.EN) "Selected video • Ready to store" else "Video đã chọn • Sẵn sàng lưu trữ",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                pickedMedia?.let { media ->
                  Text(
                    text = if (currentLanguage == AppLanguage.EN) "Duration: ${media.durationSeconds}s • Size: ${formatMediaSize(media.sizeBytes)}"
                           else "Thời lượng: ${media.durationSeconds}s • Dung lượng: ${formatMediaSize(media.sizeBytes)}",
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
                imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = Color(0xFF80DEEA),
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isVideo) "Video" else if (currentLanguage == AppLanguage.EN) "Photo" else "Ảnh",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }

        // Validation Error / Status Feedback
        validationError?.let { message ->
          Spacer(modifier = Modifier.height(6.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFFEBEE),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = message,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC62828)
              )
            }
          }
        }
        pickedMedia?.takeIf { validationError == null }?.let { media ->
          Spacer(modifier = Modifier.height(6.dp))
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
                text = if (currentLanguage == AppLanguage.EN) "Size: ${formatMediaSize(media.sizeBytes)} • Ready ✓" else "Dung lượng: ${formatMediaSize(media.sizeBytes)} • Sẵn sàng ✓",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1B5E20)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Single Unified File Upload Button
        Button(
          onClick = {
            filePickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          enabled = !isSaving,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("btn_upload_media_file")
        ) {
          Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (currentLanguage == AppLanguage.EN) "📁 Upload File (Photo or Video)" else "📁 Tải file lên (Ảnh hoặc Video)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Thumbnails Row (if photo and presets available)
        if (!isVideo && presetPhotos.isNotEmpty()) {
          Text(
            text = if (currentLanguage == AppLanguage.EN) "Or choose from romantic sample photos:" else "Hoặc chọn nhanh ảnh mẫu lãng mạn:",
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
                  .size(50.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFFFF2D75) else Color(0xFFFFE0E9),
                    shape = RoundedCornerShape(12.dp)
                  )
                  .clickable {
                    selectedMediaUri = url
                    pickedMedia = null
                    validationError = null
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
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Title Input
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(if (currentLanguage == AppLanguage.EN) "Moment Title" else "Tiêu đề khoảnh khắc") },
          placeholder = { Text(if (currentLanguage == AppLanguage.EN) "e.g., Sunset by the beach..." else "Ví dụ: Hoàng hôn bên bờ biển...") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("input_memory_title"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFF2D75),
            unfocusedBorderColor = Color(0xFFFFDDE6)
          )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Date input (Location removed per user request)
        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text(if (currentLanguage == AppLanguage.EN) "Date (dd/MM/yyyy)" else "Ngày kỷ niệm") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("input_memory_date"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFF2D75),
            unfocusedBorderColor = Color(0xFFFFDDE6)
          )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Note Input (Supports long multiline notes)
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text(if (currentLanguage == AppLanguage.EN) "Loving Message & Thoughts" else "Lời nhắn & cảm xúc yêu thương") },
          placeholder = { Text(if (currentLanguage == AppLanguage.EN) "Write down your sweet thoughts and unforgettable emotions..." else "Ghi lại cảm xúc ngọt ngào khi ở bên người ấy...") },
          minLines = 3,
          maxLines = 8,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("input_memory_note"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFF2D75),
            unfocusedBorderColor = Color(0xFFFFDDE6)
          )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy Options (Only 2 modes: Couple Only & Private)
        Text(
          text = if (currentLanguage == AppLanguage.EN) "Privacy & Viewing Mode:" else "Quyền Xem & Riêng Tư:",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF880E4F)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          // Couple Only
          Surface(
            shape = RoundedCornerShape(14.dp),
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
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = if (privacyLevel == "COUPLE_ONLY") Color(0xFFE91E63) else Color.Gray,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (currentLanguage == AppLanguage.EN) "Couple Only" else "Chỉ 2 người",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (privacyLevel == "COUPLE_ONLY") Color(0xFF880E4F) else Color.Gray
              )
              Text(
                text = if (currentLanguage == AppLanguage.EN) "Visible to you & partner" else "Chỉ bạn và người thương thấy",
                fontSize = 10.sp,
                color = Color.Gray
              )
            }
          }

          // Private / Just Me
          Surface(
            shape = RoundedCornerShape(14.dp),
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
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = if (privacyLevel == "PRIVATE") Color(0xFF7E57C2) else Color.Gray,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (currentLanguage == AppLanguage.EN) "Just Me" else "Chỉ mình tôi",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (privacyLevel == "PRIVATE") Color(0xFF4A148C) else Color.Gray
              )
              Text(
                text = if (currentLanguage == AppLanguage.EN) "Keep this private" else "Riêng tư cá nhân",
                fontSize = 10.sp,
                color = Color.Gray
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Upload Status Spinner
        if (isSaving) {
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
              text = if (currentLanguage == AppLanguage.EN) "Saving memory safely... ✨" else "Đang lưu trữ kỷ niệm an toàn... ✨",
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
            enabled = !isSaving,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text(if (currentLanguage == AppLanguage.EN) "Cancel" else strings.btnCancel)
          }

          val canSave = !isSaving && validationError == null

          Button(
            onClick = {
              isSaving = true
              coroutineScope.launch {
                val finalTitle = title.ifBlank {
                  if (currentLanguage == AppLanguage.VI) "Khoảnh Khắc Kỷ Niệm" else "Special Moment"
                }
                // A preset has no picked file; a picked file uses its cover frame as the local photo.
                val saved = try {
                  onSaveMemory(
                    finalTitle,
                    dateText,
                    selectedMediaUri,
                    note,
                    location,
                    pickedMedia,
                    if (pickedMedia != null) selectedMediaUri else null,
                    privacyLevel
                  )
                } finally {
                  isSaving = false
                }
                if (saved) onDismiss()
              }
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1.2f).testTag("btn_save_memory_submit")
          ) {
            Icon(imageVector = Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (currentLanguage == AppLanguage.EN) "Save Memory" else "Lưu Kỷ Niệm",
              fontWeight = FontWeight.Bold
            )
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
  isEnglish: Boolean = false,
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF8E24AA))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isEnglish) "Edit Memory" else "Chỉnh Sửa Kỷ Niệm",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF880E4F)
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = if (isEnglish) "Close" else "Đóng")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(if (isEnglish) "Title" else "Tiêu đề") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text(if (isEnglish) "Anniversary Date (dd/MM/yyyy)" else "Ngày kỷ niệm") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text(if (isEnglish) "Loving Note & Message" else "Ghi chú & Lời nhắn yêu thương") },
          minLines = 3,
          maxLines = 8,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = if (isEnglish) "Privacy & Viewing Mode:" else "Quyền xem & Riêng tư:",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF880E4F)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FilterChip(
            selected = privacyLevel == "COUPLE_ONLY",
            onClick = { privacyLevel = "COUPLE_ONLY" },
            // Đã riêng tư thì không chuyển lại chia sẻ: ảnh có thể đã từng nằm trên đám mây.
            enabled = memory.privacyLevel != PRIVACY_PRIVATE,
            label = { Text(if (isEnglish) "💑 Couple Only" else "💑 Chỉ 2 người", fontSize = 11.5.sp) }
          )
          FilterChip(
            selected = privacyLevel == "PRIVATE",
            onClick = { privacyLevel = "PRIVATE" },
            label = { Text(if (isEnglish) "🔒 Just Me" else "🔒 Chỉ mình tôi", fontSize = 11.5.sp) }
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text(if (isEnglish) "Cancel" else "Hủy")
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
            Text(if (isEnglish) "Save Changes" else "Lưu cập nhật")
          }
        }
      }
    }
  }
}


/**
 * Memory Detail Dialog with Video Player, Storage Card & Role-based Permissions
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
  onDelete: () -> Unit,
  resolveMedia: suspend () -> String?
) {
  val isVideo = memory.mediaType == "VIDEO"
  val hasCloud = !memory.cloudinaryPublicId.isNullOrBlank()
  // The local copy plays on the author's device; a partner's device resolves a short-lived URL.
  val mediaUri = rememberMemoryUri(
    memoryId = memory.syncId,
    localUri = if (isVideo) memory.videoUri else memory.photoUri,
    cloudPublicId = memory.cloudinaryPublicId,
    resolve = resolveMedia
  )
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
          if (isVideo) {
            // key() rebuilds the player when the resolved URL changes, so it never plays a stale source.
            mediaUri?.let { uri ->
              key(uri) {
                AndroidView(
                  factory = { ctx ->
                    VideoView(ctx).apply {
                      setVideoURI(Uri.parse(uri))
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
              }
            }
          } else {
            AsyncImage(
              model = mediaUri,
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
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            IconButton(
              onClick = onToggleFavorite,
              modifier = Modifier
                .size(48.dp)
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
            color = if (hasCloud) Color(0xFFE0F2F1) else Color(0xFFFFF3E0),
            border = androidx.compose.foundation.BorderStroke(
              1.dp, if (hasCloud) Color(0xFF80CBC4) else Color(0xFFFFB74D)
            )
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(
                imageVector = when {
                  isVideo -> Icons.Default.Movie
                  hasCloud -> Icons.Default.Cloud
                  else -> Icons.Default.PhoneAndroid
                },
                contentDescription = null,
                tint = if (hasCloud) Color(0xFF00796B) else Color(0xFFE65100),
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = when {
                  isVideo -> "Video ${memory.durationSeconds}s"
                  hasCloud -> if (currentLanguage == AppLanguage.EN) "Cloud photo" else "Ảnh đám mây"
                  else -> if (currentLanguage == AppLanguage.EN) "On device" else "Lưu trên máy"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasCloud) Color(0xFF004D40) else Color(0xFFE65100)
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

        // Storage card: a memory with no publicId was never uploaded, so it reads as on-device only.
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (hasCloud) Color(0xFFF1F8E9) else Color(0xFFFFF3E0),
          border = androidx.compose.foundation.BorderStroke(
            1.dp, if (hasCloud) Color(0xFFC5E1A5) else Color(0xFFFFB74D)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (hasCloud) Icons.Default.CloudDone else Icons.Default.PhoneAndroid,
                  contentDescription = null,
                  tint = if (hasCloud) Color(0xFF33691E) else Color(0xFFE65100),
                  modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (hasCloud) {
                    if (currentLanguage == AppLanguage.EN) "Secure Cloud Storage" else "Lưu Trữ Đám Mây An Toàn"
                  } else {
                    if (currentLanguage == AppLanguage.EN) "On-device only" else "Chỉ lưu trên máy"
                  },
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (hasCloud) Color(0xFF33691E) else Color(0xFFE65100)
                )
              }
            }

            if (memory.fileSizeFormatted.isNotBlank()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (currentLanguage == AppLanguage.EN) "File size: ${memory.fileSizeFormatted}" else "Dung lượng: ${memory.fileSizeFormatted}",
                fontSize = 11.5.sp,
                color = Color(0xFF558B2F)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Permissions & Role Notice
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = if (isAuthor) Color(0xFFEDE7F6) else Color(0xFFFFF3E0),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAuthor) Color(0xFFB39DDB) else Color(0xFFFFCC80)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
          ) {
            Icon(
              imageVector = if (isAuthor) Icons.Default.Person else Icons.Default.Lock,
              contentDescription = null,
              tint = if (isAuthor) Color(0xFF512DA8) else Color(0xFFE65100),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isAuthor) {
                if (currentLanguage == AppLanguage.EN) "You are the creator of this memory • Full edit and delete permissions."
                else "Bạn là tác giả của kỷ niệm này • Có toàn quyền chỉnh sửa và xóa."
              } else {
                if (currentLanguage == AppLanguage.EN) "Creator: ${memory.authorName.ifBlank { "Partner" }} • View and favorite permissions."
                else "Tác giả: ${memory.authorName.ifBlank { "Người ấy" }} • Bạn có quyền xem và thả tim yêu thích."
              },
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = if (isAuthor) Color(0xFF311B92) else Color(0xFFBF360C)
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

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
              Text(if (currentLanguage == AppLanguage.EN) "Cancel" else strings.btnCancel)
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
              Text(if (currentLanguage == AppLanguage.EN) "Confirm Delete" else "Xác nhận xóa")
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
                  Text(if (currentLanguage == AppLanguage.EN) "Delete" else "Xóa")
                }

                Button(
                  onClick = onEdit,
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))
                ) {
                  Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(if (currentLanguage == AppLanguage.EN) "Edit" else "Sửa")
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
              Text(if (currentLanguage == AppLanguage.EN) "Got It" else strings.btnGotIt)
            }
          }
        }
      }
    }
  }
}