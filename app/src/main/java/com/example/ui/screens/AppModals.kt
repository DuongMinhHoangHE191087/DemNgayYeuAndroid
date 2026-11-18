@file:Suppress("FunctionName")
package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.shared.guide.GuideQuickAction
import com.example.ui.screens.guide.InLoveUserGuideDialog
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.AddPhotoAlternate
import com.example.data.media.LocalMediaStore
import com.example.ui.components.AppDialog
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// 1. LANGUAGE SELECTION DIALOG (BILINGUAL)
// ==========================================
@Composable
fun LanguageSelectionDialog(
  currentLanguage: AppLanguage,
  onLanguageSelected: (AppLanguage) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedLang by remember { mutableStateOf(currentLanguage) }
  val strings = LocalizedStrings.get(selectedLang)

  AppDialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 14.dp,
      modifier = Modifier
        .fillMaxWidth()
        .animateDialogEntrance()
        .border(
          width = 1.5.dp,
          brush = Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFFF80AB))),
          shape = RoundedCornerShape(28.dp)
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Icon header
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(listOf(RoseGradientStart, RoseGradientMid))
            )
            .shadow(4.dp, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = strings.langDialogTitle,
          fontSize = 18.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFF26071B),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = strings.langDialogSubtitle,
          fontSize = 12.5.sp,
          color = Color(0xFF6B2B50),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Vietnamese Option
        LanguageOptionCard(
          title = stringResource(R.string.lang_vietnamese),
          sub = stringResource(R.string.lang_vi_desc),
          flag = "🇻🇳",
          isSelected = selectedLang == AppLanguage.VI,
          onClick = { selectedLang = AppLanguage.VI },
          testTag = "lang_option_vi"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // English Option
        LanguageOptionCard(
          title = stringResource(R.string.lang_english),
          sub = stringResource(R.string.lang_en_desc),
          flag = "🇬🇧",
          isSelected = selectedLang == AppLanguage.EN,
          onClick = { selectedLang = AppLanguage.EN },
          testTag = "lang_option_en"
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          SecondaryButton(
            text = strings.btnCancel,
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MinTouchBox)
          )

          PrimaryGradientButton(
            text = strings.btnConfirm,
            onClick = {
              onLanguageSelected(selectedLang)
              onDismiss()
            },
            modifier = Modifier
              .weight(1.2f)
              .heightIn(min = MinTouchBox),
            testTag = "btn_confirm_language"
          )
        }
      }
    }
  }
}

@Composable
private fun LanguageOptionCard(
  title: String,
  sub: String,
  flag: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (isSelected) Color(0xFFFFF0F5) else Color.White,
    shadowElevation = if (isSelected) 3.dp else 1.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable { onClick() }
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) Primary else Color(0xFFFFCDD2).copy(alpha = 0.6f),
        shape = RoundedCornerShape(18.dp)
      )
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(text = flag, fontSize = 28.sp)
        Column {
          Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF26071B)
          )
          Text(
            text = sub,
            fontSize = 11.5.sp,
            color = Color(0xFF6B2B50)
          )
        }
      }

      if (isSelected) {
        Box(
          modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Primary),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

// ==========================================
// 2. INTERACTIVE 4-CHAPTER USER GUIDE
// ==========================================
@Composable
fun UserGuideDialog(
  language: AppLanguage,
  onDismiss: () -> Unit,
  onQuickAction: (GuideQuickAction) -> Unit = {},
) = InLoveUserGuideDialog(
  language = language,
  onDismiss = onDismiss,
  onQuickAction = onQuickAction,
)

// ==========================================
// 3. WALLPAPER PICKER DIALOG
// ==========================================
data class WallpaperPreset(
  val titleVi: String,
  val titleEn: String,
  val url: String
)

val WALLPAPER_PRESETS = listOf(
  WallpaperPreset(
    "Hoa Anh Đào",
    "Cherry Blossom",
    "https://images.unsplash.com/photo-1522383225653-ed111181a951?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Hoàng Hôn Hồng",
    "Sunset Rose",
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Đêm Sao Lãng Mạn",
    "Starry Night",
    "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Vườn Hồng Mộng Mơ",
    "Rose Garden",
    "https://images.unsplash.com/photo-1496062031456-07b8f162a322?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Cực Quang Hồng",
    "Pink Aurora",
    "https://images.unsplash.com/photo-1531366936337-7c912a4589a7?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Bầu Trời Pastel",
    "Pastel Clouds",
    "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Vườn Oải Hương",
    "Lavender Dream",
    "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Ánh Đèn Lung Linh",
    "Fairy Lights",
    "https://images.unsplash.com/photo-1513151233558-d860c5398176?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Đêm Paris Lung Linh",
    "Paris Twilight",
    "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Nắng Vàng Mùa Thu",
    "Golden Autumn",
    "https://images.unsplash.com/photo-1477414348463-c0eb7f1359b6?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Bình Minh Tình Yêu",
    "Love Sunrise",
    "https://images.unsplash.com/photo-1495616811223-4d98c6e9c869?q=80&w=1080&auto=format&fit=crop"
  ),
  WallpaperPreset(
    "Ngọn Nến Lãng Mạn",
    "Warm Candlelight",
    "https://images.unsplash.com/photo-1603006905003-be475563bc59?q=80&w=1080&auto=format&fit=crop"
  )
)

@Composable
fun WallpaperPickerDialog(
  language: AppLanguage,
  currentWallpaperUrl: String,
  presetWallpapers: List<String> = emptyList(),
  onApplyWallpaper: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val strings = LocalizedStrings.get(language)
  var selectedUrl by remember { mutableStateOf(currentWallpaperUrl) }

  val scope = rememberCoroutineScope()
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    uri?.let {
      scope.launch {
        val copied = withContext(Dispatchers.IO) { LocalMediaStore.copyPickedUri(context, it) }
        selectedUrl = copied?.let { file -> Uri.fromFile(file).toString() } ?: it.toString()
      }
    }
  }

  val activePresets = remember(presetWallpapers) {
    if (presetWallpapers.isNotEmpty()) {
      val defaultPresets = WALLPAPER_PRESETS
      val customPresets = presetWallpapers.mapIndexed { index, url ->
        val fallback = defaultPresets.getOrNull(index % defaultPresets.size)
        WallpaperPreset(
          titleVi = fallback?.titleVi ?: "Hình nền ${index + 1}",
          titleEn = fallback?.titleEn ?: "Wallpaper ${index + 1}",
          url = url
        )
      }
      (customPresets + defaultPresets).distinctBy { it.url }
    } else {
      WALLPAPER_PRESETS
    }
  }

  AppDialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 14.dp,
      modifier = Modifier
        .fillMaxWidth()
        .animateDialogEntrance()
        .border(
          width = 1.5.dp,
          brush = Brush.horizontalGradient(
            listOf(Color(0xFFFF4081), Color(0xFFFF80AB))
          ),
          shape = RoundedCornerShape(28.dp)
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Title
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
                .background(
                  Brush.linearGradient(listOf(RoseGradientStart, RoseGradientMid))
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Wallpaper,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = strings.wallpaperTitle,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF26071B)
            )
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(MinTouchBox)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF6B2B50))
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Button: Upload from Device (custom option offered alongside presets)
        SecondaryButton(
          text = if (language == AppLanguage.VI) "Tải ảnh từ thư viện máy" else "Choose Photo from Device",
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          icon = Icons.Filled.AddPhotoAlternate,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchBox),
          testTag = "btn_upload_wallpaper_device"
        )

        // Custom image preview if picked from device
        if (selectedUrl.startsWith("/") || selectedUrl.startsWith("file:") || selectedUrl.startsWith("content:")) {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFCE4EC),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary),
            modifier = Modifier
              .fillMaxWidth()
              .height(72.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(8.dp)
            ) {
              AsyncImage(
                model = selectedUrl,
                contentDescription = "Custom wallpaper preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(56.dp)
                  .clip(RoundedCornerShape(10.dp))
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = if (language == AppLanguage.VI) "Ảnh cá nhân của bạn ✓" else "Your custom photo ✓",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = Primary
                )
                Text(
                  text = if (language == AppLanguage.VI) "Đã sẵn sàng áp dụng làm hình nền" else "Ready to apply as wallpaper",
                  fontSize = 10.5.sp,
                  color = Color(0xFF880E4F)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Grid / Rows
        Text(
          text = if (language == AppLanguage.VI) "Bộ sưu tập phông nền lãng mạn (${activePresets.size}):" else "Curated Romantic Presets (${activePresets.size}):",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        activePresets.chunked(2).forEach { rowPresets ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rowPresets.forEach { preset ->
              WallpaperCardItem(
                preset = preset,
                isSelected = selectedUrl == preset.url,
                language = language,
                onClick = { selectedUrl = preset.url },
                modifier = Modifier.weight(1f)
              )
            }
            if (rowPresets.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Apply Button
        PrimaryGradientButton(
          text = strings.btnApplyWallpaper,
          onClick = {
            onApplyWallpaper(selectedUrl)
            onDismiss()
          },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchBox),
          testTag = "btn_apply_wallpaper"
        )
      }
    }
  }
}

@Composable
private fun WallpaperCardItem(
  preset: WallpaperPreset,
  isSelected: Boolean,
  language: AppLanguage,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFFFFE9F0),
    shadowElevation = if (isSelected) 4.dp else 1.dp,
    modifier = modifier
      .height(84.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .border(
        width = if (isSelected) 2.5.dp else 1.dp,
        color = if (isSelected) Primary else Color(0xFFFFCDD2).copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp)
      )
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = preset.url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              0.45f to Color.Transparent,
              1f to Color.Black.copy(alpha = 0.55f)
            )
          )
      )
      Text(
        text = if (language == AppLanguage.VI) preset.titleVi else preset.titleEn,
        color = Color.White,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(8.dp)
      )

      if (isSelected) {
        Box(
          modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Primary)
            .align(Alignment.TopEnd)
            .padding(2.dp),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
      }
    }
  }
}
