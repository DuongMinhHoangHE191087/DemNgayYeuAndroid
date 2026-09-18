@file:Suppress("FunctionName")
package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.SharedMemoryEntity
import com.example.ui.theme.Primary
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 'Memories' widget on the couple dashboard displaying an interactive carousel of photos
 * from the user's gallery associated with specific anniversaries, powered by Coil.
 */
@Composable
fun AnniversaryMemoriesWidget(
  viewModel: InLoveViewModel,
  onNavigateToFullAlbum: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val memories by viewModel.sharedMemories.collectAsState()
  val milestones by viewModel.milestones.collectAsState()
  val anniversaryDate by viewModel.anniversaryDate.collectAsState()
  val selectedFilter by viewModel.selectedAnniversaryFilter.collectAsState()

  // State for Add Photo dialog
  var showAddDialog by remember { mutableStateOf(false) }
  var pendingPhotoUri by remember { mutableStateOf<String?>(null) }
  var selectedDetailMemory by remember { mutableStateOf<SharedMemoryEntity?>(null) }

  // Gallery Photo Picker Launcher safely guarded for runtime and test environments
  val registryOwner = LocalActivityResultRegistryOwner.current
  val galleryLauncher = if (registryOwner != null) {
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
      uri?.let {
        val savedPath = copyPhotoUriToStorage(context, it)
        pendingPhotoUri = savedPath ?: it.toString()
        showAddDialog = true
      }
    }
  } else null

  val onTriggerPhotoPick: () -> Unit = {
    if (galleryLauncher != null) {
      galleryLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
      )
    } else {
      pendingPhotoUri = ""
      showAddDialog = true
    }
  }

  // Predefined core anniversaries list
  val defaultAnniversaryList = remember(anniversaryDate, milestones, memories) {
    val initialAnniversary = if (anniversaryDate.isNotBlank()) "$anniversaryDate - Ngày Bắt Đầu Yêu" else "Ngày Bắt Đầu Yêu"
    val list = mutableListOf(
      initialAnniversary,
      "Kỷ Niệm 1 Năm Yêu Nhau",
      "100 Ngày Bên Nhau",
      "14/02 - Lễ Tình Nhân Valentine",
      "Chuyến Du Lịch Đầu Tiên"
    )
    // Add anniversaries from milestones
    milestones.forEach { m ->
      val title = m.title
      if (title.isNotBlank() && !list.contains(title)) {
        list.add(title)
      }
    }
    // Add any custom ones present in memories
    memories.forEach { mem ->
      if (mem.anniversaryTitle.isNotBlank() && !list.contains(mem.anniversaryTitle)) {
        list.add(mem.anniversaryTitle)
      }
    }
    list
  }

  // Filter memories according to selected filter
  val filteredMemories = remember(memories, selectedFilter) {
    if (selectedFilter == "Tất cả" || selectedFilter == "All") {
      memories
    } else {
      memories.filter { it.anniversaryTitle.equals(selectedFilter, ignoreCase = true) }
    }
  }

  val isVietnamese = appLanguage == AppLanguage.VI

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = Color.White.copy(alpha = 0.98f)
    ),
    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFC6DB)),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("memories_widget")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp)
        .animateContentSize()
    ) {
      // 1. WIDGET HEADER
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFFFF4081), Color(0xFFFF80AB))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.PhotoLibrary,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = if (isVietnamese) "KHOẢNH KHẮC KỶ NIỆM" else "ANNIVERSARY MEMORIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary,
                letterSpacing = 0.8.sp
              )
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFE4EC),
                modifier = Modifier.padding(bottom = 1.dp)
              ) {
                Text(
                  text = "${filteredMemories.size} ảnh",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFC2185B),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Text(
              text = if (isVietnamese) "Ghi dấu từng ngày kỷ niệm" else "Captured Couple Moments",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF26071B)
            )
          }
        }

        // Add from Gallery Button
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFFFFF0F5),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB3C6)),
          modifier = Modifier
            .clickable { onTriggerPhotoPick() }
            .testTag("btn_add_gallery_photo")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.AddPhotoAlternate,
              contentDescription = "Add Photo",
              tint = Primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = if (isVietnamese) "+ Thêm ảnh" else "+ Add photo",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Primary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 2. ANNIVERSARY FILTER CHIPS
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val allFilterLabel = if (isVietnamese) "Tất cả" else "All"
        val isAllSelected = selectedFilter == "Tất cả" || selectedFilter == "All"

        FilterChip(
          selected = isAllSelected,
          onClick = { viewModel.selectAnniversaryFilter(allFilterLabel) },
          label = {
            Text(
              text = if (isVietnamese) "🌟 Tất cả kỷ niệm" else "🌟 All Anniversaries",
              fontSize = 12.sp,
              fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Primary,
            selectedLabelColor = Color.White,
            containerColor = Color(0xFFFFF5F8),
            labelColor = Color(0xFF6B2B50)
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isAllSelected,
            borderColor = if (isAllSelected) Primary else Color(0xFFFFD1DC)
          ),
          shape = RoundedCornerShape(14.dp)
        )

        defaultAnniversaryList.forEach { anniversaryName ->
          val isSelected = selectedFilter.equals(anniversaryName, ignoreCase = true)
          val chipIcon = when {
            anniversaryName.contains("Bắt Đầu") || anniversaryName.contains("Yêu") -> "🌹"
            anniversaryName.contains("1 Năm") || anniversaryName.contains("Year") -> "💍"
            anniversaryName.contains("100") -> "💕"
            anniversaryName.contains("Valentine") || anniversaryName.contains("14/02") -> "🍫"
            anniversaryName.contains("Du Lịch") -> "✈️"
            anniversaryName.contains("Sinh Nhật") -> "🎂"
            else -> "✨"
          }

          FilterChip(
            selected = isSelected,
            onClick = { viewModel.selectAnniversaryFilter(anniversaryName) },
            label = {
              Text(
                text = "$chipIcon $anniversaryName",
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Primary,
              selectedLabelColor = Color.White,
              containerColor = Color(0xFFFFF5F8),
              labelColor = Color(0xFF6B2B50)
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSelected,
              borderColor = if (isSelected) Primary else Color(0xFFFFD1DC)
            ),
            shape = RoundedCornerShape(14.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 3. CAROUSEL OF PHOTOS USING COIL
      if (filteredMemories.isEmpty()) {
        // Empty State Card for the selected anniversary
        EmptyAnniversaryPhotoCard(
          anniversaryName = selectedFilter,
          isVietnamese = isVietnamese,
          onPickGallery = onTriggerPhotoPick
        )
      } else {
        val pagerState = rememberPagerState(pageCount = { filteredMemories.size })

        Column(modifier = Modifier.fillMaxWidth()) {
          HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
              .fillMaxWidth()
              .height(230.dp)
          ) { pageIndex ->
            val memory = filteredMemories[pageIndex]
            AnniversaryPhotoCarouselItem(
              memory = memory,
              isVietnamese = isVietnamese,
              onClick = { selectedDetailMemory = memory },
              onToggleFavorite = { viewModel.toggleMemoryFavorite(memory) }
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Pager Indicator & Quick Navigation Controls
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Previous Slide Button
            IconButton(
              onClick = {
                if (pagerState.currentPage > 0) {
                  coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                  }
                }
              },
              enabled = pagerState.currentPage > 0,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = "Previous Photo",
                tint = if (pagerState.currentPage > 0) Primary else Color.LightGray
              )
            }

            // Dot & Heart Page Indicators
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              repeat(filteredMemories.size.coerceAtMost(8)) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                  modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                      if (isSelected) Primary else Color(0xFFFFCDD2)
                    )
                )
              }
              if (filteredMemories.size > 8) {
                Text(
                  text = "+${filteredMemories.size - 8}",
                  fontSize = 10.sp,
                  color = Primary,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Next Slide Button
            IconButton(
              onClick = {
                if (pagerState.currentPage < filteredMemories.size - 1) {
                  coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                  }
                }
              },
              enabled = pagerState.currentPage < filteredMemories.size - 1,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = "Next Photo",
                tint = if (pagerState.currentPage < filteredMemories.size - 1) Primary else Color.LightGray
              )
            }
          }
        }
      }

      // 4. BOTTOM ACTION TO VIEW FULL ALBUM
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onNavigateToFullAlbum,
          modifier = Modifier.testTag("btn_view_full_memories")
        ) {
          Icon(
            imageVector = Icons.Filled.PhotoAlbum,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isVietnamese) "Xem tất cả ${memories.size} ảnh trong Album" else "View all ${memories.size} photos in Album",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary
          )
        }

        Text(
          text = if (isVietnamese) "Vuốt để xem thêm •" else "Swipe for more •",
          fontSize = 11.sp,
          color = Color(0xFF9E778E)
        )
      }
    }
  }

  // DIALOG 1: ADD PHOTO FROM GALLERY TO SPECIFIC ANNIVERSARY
  if (showAddDialog && pendingPhotoUri != null) {
    AddAnniversaryPhotoDialog(
      initialPhotoUri = pendingPhotoUri!!,
      preselectedAnniversary = if (selectedFilter != "Tất cả" && selectedFilter != "All") selectedFilter else (defaultAnniversaryList.firstOrNull() ?: "Ngày Bắt Đầu Yêu"),
      anniversaryOptions = defaultAnniversaryList,
      isVietnamese = isVietnamese,
      onDismiss = {
        showAddDialog = false
        pendingPhotoUri = null
      },
      onSave = { title, dateText, uri, note, location, anniversaryTitle ->
        viewModel.addSharedMemory(
          title = title,
          dateText = dateText,
          photoUri = uri,
          note = note,
          location = location,
          anniversaryTitle = anniversaryTitle
        )
        showAddDialog = false
        pendingPhotoUri = null
      }
    )
  }

  // DIALOG 2: MEMORY PHOTO DETAIL VIEWER
  selectedDetailMemory?.let { memory ->
    AnniversaryPhotoDetailDialog(
      memory = memory,
      isVietnamese = isVietnamese,
      onDismiss = { selectedDetailMemory = null },
      onToggleFavorite = { viewModel.toggleMemoryFavorite(memory) },
      onDelete = {
        viewModel.deleteSharedMemory(memory.id)
        selectedDetailMemory = null
      }
    )
  }
}

/**
 * Individual Card in the Anniversary Photo Carousel using Coil for image loading.
 */
@Composable
private fun AnniversaryPhotoCarouselItem(
  memory: SharedMemoryEntity,
  isVietnamese: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit
) {
  val context = LocalContext.current

  Card(
    shape = RoundedCornerShape(20.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxSize()
      .clip(RoundedCornerShape(20.dp))
      .border(1.2.dp, Color(0xFFFFD1DC), RoundedCornerShape(20.dp))
      .clickable { onClick() }
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // 1. COIL IMAGE LOADER WITH SHIMMER & ERROR HANDLING
      SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
          .data(memory.photoUri)
          .crossfade(true)
          .build(),
        contentDescription = memory.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFFFFF0F5)),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(
              color = Primary,
              modifier = Modifier.size(32.dp),
              strokeWidth = 2.5.dp
            )
          }
        },
        error = {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Ảnh kỷ niệm",
                fontSize = 11.sp,
                color = Primary,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      )

      // 2. TOP GRADIENT SCRIM FOR BADGES & FAVORITE
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
            )
          )
      )

      // TOP BAR: ANNIVERSARY BADGE & FAVORITE BUTTON
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(10.dp)
          .align(Alignment.TopCenter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Anniversary Tag Pill
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color.Black.copy(alpha = 0.45f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.VolunteerActivism,
              contentDescription = null,
              tint = Color(0xFFFF80AB),
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = memory.anniversaryTitle.ifBlank { "Kỷ Niệm Tình Yêu" },
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Favorite Heart Button
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier
            .size(34.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
          Icon(
            imageVector = if (memory.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (memory.isFavorite) Color(0xFFFF2D75) else Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // 3. BOTTOM GRADIENT SCRIM FOR CAPTION & DETAILS
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.65f),
                Color.Black.copy(alpha = 0.9f)
              )
            )
          )
          .padding(horizontal = 14.dp, vertical = 12.dp)
      ) {
        Column {
          Text(
            text = memory.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Spacer(modifier = Modifier.height(2.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Date
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFFFFB3C6),
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = memory.dateText,
                color = Color(0xFFFFE4EC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }

            // Location if available
            if (memory.location.isNotBlank()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.LocationOn,
                  contentDescription = null,
                  tint = Color(0xFFFFD54F),
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = memory.location,
                  color = Color.White.copy(alpha = 0.85f),
                  fontSize = 11.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          if (memory.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = memory.note,
              color = Color.White.copy(alpha = 0.85f),
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              lineHeight = 15.sp
            )
          }
        }
      }
    }
  }
}

/**
 * Friendly empty card when a chosen anniversary has no photos yet.
 */
@Composable
private fun EmptyAnniversaryPhotoCard(
  anniversaryName: String,
  isVietnamese: Boolean,
  onPickGallery: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color(0xFFFFF7F9),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD1DC)),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(CircleShape)
          .background(Color(0xFFFFE4EC)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.PhotoAlbum,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(26.dp)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = if (isVietnamese) "Chưa có ảnh cho \"$anniversaryName\"" else "No photos for \"$anniversaryName\"",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF26071B),
        textAlign = TextAlign.Center
      )

      Text(
        text = if (isVietnamese) "Hãy chọn những bức ảnh ngọt ngào từ thư viện để lưu giữ khoảnh khắc này nhé!"
        else "Select sweet photos from your device gallery to capture this special occasion!",
        fontSize = 12.sp,
        color = Color(0xFF6B2B50),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
      )

      Button(
        onClick = onPickGallery,
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(14.dp)
      ) {
        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isVietnamese) "Chọn ảnh từ Thư Viện" else "Choose from Gallery",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        )
      }
    }
  }
}

/**
 * Dialog for adding a photo from gallery and associating it with a specific anniversary.
 */
@Composable
private fun AddAnniversaryPhotoDialog(
  initialPhotoUri: String,
  preselectedAnniversary: String,
  anniversaryOptions: List<String>,
  isVietnamese: Boolean,
  onDismiss: () -> Unit,
  onSave: (title: String, dateText: String, photoUri: String, note: String, location: String, anniversaryTitle: String) -> Unit
) {
  val context = LocalContext.current
  val todayFormatted = remember {
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
  }

  var title by remember { mutableStateOf("") }
  var dateText by remember { mutableStateOf(todayFormatted) }
  var note by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }
  var selectedAnniversary by remember { mutableStateOf(preselectedAnniversary) }
  var currentPhotoUri by remember { mutableStateOf(initialPhotoUri) }

  // Launcher to change photo if desired (safely guarded)
  val registryOwner = LocalActivityResultRegistryOwner.current
  val rePickerLauncher = if (registryOwner != null) {
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
      uri?.let {
        val saved = copyPhotoUriToStorage(context, it)
        currentPhotoUri = saved ?: it.toString()
      }
    }
  } else null

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = Color.White,
      shadowElevation = 12.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.2.dp, Color(0xFFFFC6DB), RoundedCornerShape(26.dp))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Top Title
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE4EC)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = if (isVietnamese) "Lưu Ảnh Kỷ Niệm" else "Save Anniversary Photo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26071B)
              )
              Text(
                text = if (isVietnamese) "Gắn ảnh từ thư viện với ngày kỷ niệm" else "Attach gallery photo to an anniversary",
                fontSize = 11.sp,
                color = Color(0xFF6B2B50)
              )
            }
          }

          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Image Preview with Coil & Change Button
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFFFFD1DC), RoundedCornerShape(18.dp))
        ) {
          SubcomposeAsyncImage(
            model = currentPhotoUri,
            contentDescription = "Preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(8.dp)
              .clickable {
                rePickerLauncher?.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isVietnamese) "Đổi ảnh khác" else "Change photo",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Anniversary Selection Selector
        Text(
          text = if (isVietnamese) "Thuộc ngày kỷ niệm:" else "Associated Anniversary:",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable Anniversary Selector Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          anniversaryOptions.forEach { option ->
            val isSelected = selectedAnniversary.equals(option, ignoreCase = true)
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isSelected) Primary else Color(0xFFFFF0F5),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isSelected) Primary else Color(0xFFFFD1DC)
              ),
              modifier = Modifier.clickable { selectedAnniversary = option }
            ) {
              Text(
                text = option,
                color = if (isSelected) Color.White else Color(0xFF6B2B50),
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title Input
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(if (isVietnamese) "Tiêu đề bức ảnh (VD: Nắm tay dạo phố)" else "Photo title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color(0xFFFFCDD2)
          )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Date and Location Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text(if (isVietnamese) "Ngày chụp" else "Date") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Primary,
              unfocusedBorderColor = Color(0xFFFFCDD2)
            )
          )

          OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text(if (isVietnamese) "Địa điểm" else "Location") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Primary,
              unfocusedBorderColor = Color(0xFFFFCDD2)
            )
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Romantic Note / Caption
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text(if (isVietnamese) "Cảm xúc / Lời nhắn tình yêu" else "Caption / Love note") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color(0xFFFFCDD2)
          )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Button
        Button(
          onClick = {
            val finalTitle = title.trim().ifBlank {
              if (isVietnamese) "Khoảnh Khắc Kỷ Niệm" else "Anniversary Moment"
            }
            onSave(finalTitle, dateText, currentPhotoUri, note, location, selectedAnniversary)
          },
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("btn_confirm_save_anniversary_photo")
        ) {
          Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isVietnamese) "Lưu Vào Kỷ Niệm ❤️" else "Save Memory ❤️",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 14.sp
          )
        }
      }
    }
  }
}

/**
 * Fullscreen / detailed photo viewer for a selected anniversary memory.
 */
@Composable
private fun AnniversaryPhotoDetailDialog(
  memory: SharedMemoryEntity,
  isVietnamese: Boolean,
  onDismiss: () -> Unit,
  onToggleFavorite: () -> Unit,
  onDelete: () -> Unit
) {
  var showDeleteConfirm by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = Color.White,
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.2.dp, Color(0xFFFFC6DB), RoundedCornerShape(26.dp))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // High-Resolution Image Preview
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
        ) {
          SubcomposeAsyncImage(
            model = memory.photoUri,
            contentDescription = memory.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )

          // Close button at top-end
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .background(Color.Black.copy(alpha = 0.45f), CircleShape)
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
          }

          // Favorite toggle at top-start
          IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(8.dp)
              .background(Color.Black.copy(alpha = 0.45f), CircleShape)
          ) {
            Icon(
              imageVector = if (memory.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
              contentDescription = "Favorite",
              tint = if (memory.isFavorite) Color(0xFFFF2D75) else Color.White
            )
          }

          // Anniversary Tag bottom pill
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(12.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Filled.VolunteerActivism, contentDescription = null, tint = Color(0xFFFF80AB), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = memory.anniversaryTitle.ifBlank { "Kỷ Niệm Tình Yêu" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // Details content
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
        ) {
          Text(
            text = memory.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF26071B)
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(text = memory.dateText, fontSize = 12.sp, color = Color(0xFF6B2B50), fontWeight = FontWeight.Medium)
            }

            if (memory.location.isNotBlank()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = memory.location, fontSize = 12.sp, color = Color(0xFF6B2B50))
              }
            }
          }

          if (memory.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = Color(0xFFFFF7F9),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD1DC)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "“${memory.note}”",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFF4A154B),
                modifier = Modifier.padding(14.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Bottom Action Buttons (Delete & Close)
          if (showDeleteConfirm) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedButton(
                onClick = { showDeleteConfirm = false },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Text(if (isVietnamese) "Hủy" else "Cancel")
              }

              Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Text(if (isVietnamese) "Xác nhận xóa" else "Delete", color = Color.White)
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isVietnamese) "Xóa" else "Delete")
              }

              Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(if (isVietnamese) "Đóng" else "Close", color = Color.White)
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Helper to copy picked gallery photo URI to internal app storage, ensuring persistence.
 */
private fun copyPhotoUriToStorage(context: Context, uri: Uri): String? {
  return try {
    val filename = "anniversary_mem_${System.currentTimeMillis()}.jpg"
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
