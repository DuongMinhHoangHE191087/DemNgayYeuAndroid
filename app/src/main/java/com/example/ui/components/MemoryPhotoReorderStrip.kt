package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Reorderable & Animated Multi-Photo Strip for Memory Creation.
 * Supports:
 * - Loading Shimmer & Spinner
 * - Multi-photo Preview with Cover Badge on 1st Photo
 * - Drag-and-drop / Quick-reorder between items
 * - Remove Photo badge & Add More button
 */
@Composable
fun MemoryPhotoReorderStrip(
  photoUris: List<String>,
  onPhotosReordered: (List<String>) -> Unit,
  onRemovePhoto: (Int) -> Unit,
  onAddMoreClicked: () -> Unit,
  isLoading: Boolean = false,
  isEnglish: Boolean = false,
  maxPhotos: Int = 10,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth()) {
    // Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.PhotoLibrary,
          contentDescription = null,
          tint = Color(0xFFFF2D75),
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = if (photoUris.isEmpty()) {
            if (isEnglish) "Add photos / video" else "Thêm ảnh / video kỷ niệm"
          } else {
            if (isEnglish) "Photos (${photoUris.size}/$maxPhotos)" else "Ảnh kỷ niệm (${photoUris.size}/$maxPhotos)"
          },
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF880E4F)
        )
      }

      if (photoUris.isNotEmpty()) {
        Text(
          text = if (isEnglish) "Drag / tap arrows to reorder" else "Kéo hoặc bấm mũi tên đổi ảnh bìa",
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF9E9E9E)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Body
    if (isLoading) {
      LoadingPhotoPlaceholder(isEnglish = isEnglish)
    } else if (photoUris.isEmpty()) {
      // Empty state upload trigger
      EmptyPhotoUploadBox(
        isEnglish = isEnglish,
        onClick = onAddMoreClicked
      )
    } else {
      // Reorderable Photo List
      val scrollState = rememberScrollState()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        photoUris.forEachIndexed { index, uri ->
          ReorderablePhotoThumbnail(
            uri = uri,
            index = index,
            totalCount = photoUris.size,
            isCover = index == 0,
            isEnglish = isEnglish,
            onRemove = { onRemovePhoto(index) },
            onMoveLeft = {
              if (index > 0) {
                val updated = photoUris.toMutableList()
                val item = updated.removeAt(index)
                updated.add(index - 1, item)
                onPhotosReordered(updated)
              }
            },
            onMoveRight = {
              if (index < photoUris.size - 1) {
                val updated = photoUris.toMutableList()
                val item = updated.removeAt(index)
                updated.add(index + 1, item)
                onPhotosReordered(updated)
              }
            },
            onSetAsCover = {
              if (index > 0) {
                val updated = photoUris.toMutableList()
                val item = updated.removeAt(index)
                updated.add(0, item)
                onPhotosReordered(updated)
              }
            }
          )
        }

        // Add more item
        if (photoUris.size < maxPhotos) {
          AddMorePhotoButton(
            onClick = onAddMoreClicked,
            isEnglish = isEnglish
          )
        }
      }
    }
  }
}

@Composable
private fun ReorderablePhotoThumbnail(
  uri: String,
  index: Int,
  totalCount: Int,
  isCover: Boolean,
  isEnglish: Boolean,
  onRemove: () -> Unit,
  onMoveLeft: () -> Unit,
  onMoveRight: () -> Unit,
  onSetAsCover: () -> Unit
) {
  var offsetX by remember { mutableFloatStateOf(0f) }
  var isDragging by remember { mutableStateOf(false) }

  val borderColor by animateColorAsState(
    targetValue = if (isCover) Color(0xFFFF2D75) else Color(0xFFFFDDE6),
    animationSpec = tween(200),
    label = "thumb_border"
  )

  Box(
    modifier = Modifier
      .offset { IntOffset(offsetX.roundToInt(), 0) }
      .zIndex(if (isDragging) 10f else 1f)
      .size(width = 82.dp, height = 82.dp)
      .shadow(if (isCover) 4.dp else 1.dp, RoundedCornerShape(16.dp))
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(if (isCover) 2.2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
      .pointerInput(uri) {
        detectDragGestures(
          onDragStart = { isDragging = true },
          onDragEnd = {
            isDragging = false
            if (offsetX < -40f && index > 0) {
              onMoveLeft()
            } else if (offsetX > 40f && index < totalCount - 1) {
              onMoveRight()
            }
            offsetX = 0f
          },
          onDragCancel = {
            isDragging = false
            offsetX = 0f
          },
          onDrag = { change, dragAmount ->
            change.consume()
            offsetX = (offsetX + dragAmount.x).coerceIn(-60f, 60f)
          }
        )
      }
      .testTag("memory_photo_thumb_$index")
  ) {
    // Photo Image
    AsyncImage(
      model = uri,
      contentDescription = "Selected photo $index",
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxSize()
        .clickable { if (!isCover) onSetAsCover() }
    )

    // Cover Badge
    if (isCover) {
      Surface(
        color = Color(0xFFFF2D75),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 8.dp),
        modifier = Modifier
          .align(Alignment.TopStart)
          .zIndex(2f)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp)
          )
          Text(
            text = if (isEnglish) "Cover" else "Bìa",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Quick Reorder Arrows (Small Overlay Controls)
    if (totalCount > 1) {
      Row(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(Color.Black.copy(alpha = 0.42f))
          .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (index > 0) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clickable { onMoveLeft() },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = "Move left",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        } else {
          Spacer(modifier = Modifier.size(20.dp))
        }

        Text(
          text = "${index + 1}",
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold
        )

        if (index < totalCount - 1) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clickable { onMoveRight() },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Move right",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        } else {
          Spacer(modifier = Modifier.size(20.dp))
        }
      }
    }

    // Remove Button (Top-Right)
    Surface(
      shape = CircleShape,
      color = Color.Black.copy(alpha = 0.65f),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(3.dp)
        .size(18.dp)
        .clickable { onRemove() }
        .zIndex(3f)
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Remove photo",
          tint = Color.White,
          modifier = Modifier.size(11.dp)
        )
      }
    }
  }
}

@Composable
private fun AddMorePhotoButton(
  onClick: () -> Unit,
  isEnglish: Boolean
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFFFFF0F5),
    border = BorderStroke(1.2.dp, Color(0xFFFFB6C1)),
    modifier = Modifier
      .size(width = 82.dp, height = 82.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag("btn_add_more_photos")
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = null,
        tint = Color(0xFFFF2D75),
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = if (isEnglish) "+ Add" else "+ Thêm",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF880E4F)
      )
    }
  }
}

@Composable
private fun EmptyPhotoUploadBox(
  isEnglish: Boolean,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xFFFFF7F9))
      .border(1.2.dp, Color(0xFFFFC0D3), RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(10.dp)
      .testTag("btn_upload_media_file")
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(46.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(
          brush = Brush.linearGradient(
            listOf(Color(0xFFFF80AB), Color(0xFFFF4081))
          )
        )
    ) {
      Icon(
        imageVector = Icons.Default.AddAPhoto,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(22.dp)
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (isEnglish) "Pick photos / video from gallery" else "Chọn ảnh / video từ thư viện",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF880E4F),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = if (isEnglish) "Select up to 10 photos • Reorder cover photo" else "Chọn tối đa 10 ảnh • Kéo thả đổi ảnh bìa",
        fontSize = 11.sp,
        color = Color.Gray,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun LoadingPhotoPlaceholder(isEnglish: Boolean) {
  val transition = rememberInfiniteTransition(label = "shimmer")
  val alpha by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.9f,
    animationSpec = infiniteRepeatable(
      animation = tween(700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "shimmer_alpha"
  )

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFFFFF0F5).copy(alpha = alpha),
    border = BorderStroke(1.2.dp, Color(0xFFFFC0D3)),
    modifier = Modifier
      .fillMaxWidth()
      .height(82.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        color = Color(0xFFFF2D75),
        strokeWidth = 2.5.dp
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = if (isEnglish) "Processing photos..." else "Đang xử lý và chuẩn bị ảnh...",
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF880E4F)
      )
    }
  }
}
