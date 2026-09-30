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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.HotPink
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
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings

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

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 14.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(
          width = 1.5.dp,
          brush = Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFFF80AB))),
          shape = RoundedCornerShape(28.dp)
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp),
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
          TextButton(
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
          ) {
            Text(
              text = strings.btnCancel,
              color = Color(0xFF6B2B50),
              fontWeight = FontWeight.SemiBold
            )
          }

          Button(
            onClick = {
              onLanguageSelected(selectedLang)
              onDismiss()
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier
              .weight(1.2f)
              .height(44.dp)
              .testTag("btn_confirm_language")
          ) {
            Text(
              text = strings.btnConfirm,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
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
// 2. COMPREHENSIVE USER GUIDE DIALOG
// ==========================================
@Composable
fun UserGuideDialog(
  language: AppLanguage,
  onDismiss: () -> Unit
) {
  val strings = LocalizedStrings.get(language)
  var selectedTab by remember { mutableIntStateOf(0) }

  val tabs = listOf(
    Pair(strings.guideTabCounter, Icons.Filled.Favorite),
    Pair(strings.guideTabCalendar, Icons.Filled.CalendarMonth),
    Pair(strings.guideTabGifts, Icons.Filled.CardGiftcard),
    Pair(strings.guideTabReminders, Icons.Filled.Notifications),
    Pair(strings.guideTabSettings, Icons.Filled.Settings)
  )

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(
          width = 1.5.dp,
          brush = Brush.horizontalGradient(
            listOf(Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFFF4081))
          ),
          shape = RoundedCornerShape(28.dp)
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(listOf(RoseGradientStart, RoseGradientMid))
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = strings.guideTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF26071B)
              )
              Text(
                text = strings.guideSubtitle,
                fontSize = 11.sp,
                color = Color(0xFF6B2B50)
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close",
              tint = Color(0xFF6B2B50)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Category Tabs
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(tabs.size) { index ->
            val (tabTitle, icon) = tabs[index]
            val isSelected = selectedTab == index
            Surface(
              shape = RoundedCornerShape(50.dp),
              color = if (isSelected) Primary else Color(0xFFFFF0F5),
              modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .clickable { selectedTab = index }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (isSelected) Color.White else Primary,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = tabTitle,
                  fontSize = 11.5.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else Color(0xFF26071B)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scrollable Guide Content per Tab
        Box(
          modifier = Modifier
            .weight(1f, fill = false)
            .height(280.dp)
            .verticalScroll(rememberScrollState())
        ) {
          when (selectedTab) {
            0 -> GuideContentSection(
              title = if (language == AppLanguage.VI) "1. Đếm Ngày Yêu & Hồ Sơ Cặp Đôi" else "1. Love Counter & Couple Profile",
              desc = if (language == AppLanguage.VI)
                "Theo dõi số ngày bên nhau với độ chính xác cao và thông tin chi tiết của 2 bạn:"
              else
                "Keep track of your exact days together and customize your couple profile:",
              points = if (language == AppLanguage.VI) listOf(
                "Đếm chính xác từng ngày từ ngày bắt đầu yêu thương của hai bạn.",
                "Chạm vào nút Trái Tim ở giữa để kích hoạt hiệu ứng Mưa Tim bay lãng mạn.",
                "Chạm vào ảnh đại diện hoặc nút 'Đồng Hành' để cập nhật tên, ngày sinh, tuổi, cung hoàng đạo của cả 2.",
                "Toàn bộ hồ sơ được lưu trữ vĩnh viễn và an toàn trong Room SQLite Database."
              ) else listOf(
                "Accurately counts every single day from your anniversary start date.",
                "Tap the beating heart in the center to trigger floating romantic hearts animation.",
                "Tap partner avatars or the 'Partner' tool button to update names, birthdays, zodiacs, and avatars.",
                "All profile changes are safely saved locally into Room SQLite Database."
              )
            )
            1 -> GuideContentSection(
              title = if (language == AppLanguage.VI) "2. Lịch Kỷ Niệm & Cột Mốc" else "2. Anniversary Calendar & Milestones",
              desc = if (language == AppLanguage.VI)
                "Ghi nhớ các dịp đặc biệt để luôn chuẩn bị trước chu đáo:"
              else
                "Never miss any special moments with smart milestone tracking:",
              points = if (language == AppLanguage.VI) listOf(
                "Xem đếm ngược số ngày đến cột mốc tiếp theo (100 ngày, 500 ngày, 1000 ngày, 1 năm...).",
                "Thêm mốc kỷ niệm tùy chỉnh mới với ảnh minh họa, tiêu đề và ngày hẹn.",
                "Bộ lọc thông minh: Kỷ niệm quan trọng, Sắp tới, Đã qua và Sinh nhật.",
                "Bật chuông thông báo để được nhắc nhở trước 7 ngày, 3 ngày và 1 ngày."
              ) else listOf(
                "View countdown to upcoming milestones (100 days, 500 days, 1-year anniversary...).",
                "Add custom milestones with photos, personalized descriptions, and date tags.",
                "Smart filtering: Important, Upcoming, Past, and Birthdays.",
                "Toggle notifications to receive alerts 7 days, 3 days, and 1 day in advance."
              )
            )
            2 -> GuideContentSection(
              title = if (language == AppLanguage.VI) "3. Gợi Ý Quà Tặng & Checklist Hẹn Hò" else "3. Gift Suggestions & Date Checklist",
              desc = if (language == AppLanguage.VI)
                "Gợi ý những món quà tinh tế và danh sách cần chuẩn bị:"
              else
                "Curated romantic gift ideas and essential date preparation checklist:",
              points = if (language == AppLanguage.VI) listOf(
                "Danh mục phong phú: Hoa tươi & Thiệp, Trang sức cặp đôi, Bữa tối lãng mạn, Quà công nghệ.",
                "Nhấn vào biểu tượng Trái Tim trên từng món để lưu vào danh sách Yêu Thích.",
                "Xem chi tiết địa điểm gợi ý và bí quyết chuẩn bị bất ngờ cho đối phương.",
                "Danh sách chuẩn bị (Checklist): Đánh dấu đồ đã chuẩn bị hoặc thêm đầu việc mới."
              ) else listOf(
                "Diverse categories: Flowers & Cards, Couple Jewelry, Candlelight Dinners, Tech Gifts.",
                "Tap the heart icon on any gift card to add it to your Wishlist.",
                "View venue recommendations, budgeting, and romantic tips for each surprise.",
                "Interactive Checklist: Check off prepared items and add custom date to-dos."
              )
            )
            3 -> GuideContentSection(
              title = if (language == AppLanguage.VI) "4. Trung Tâm Nhắc Hẹn Thông Minh" else "4. Smart Date Reminders",
              desc = if (language == AppLanguage.VI)
                "Đảm bảo bạn luôn nhớ những ngày hẹn hò ngọt ngào:"
              else
                "Stay on top of romantic dates and weekly love routines:",
              points = if (language == AppLanguage.VI) listOf(
                "Kích hoạt chu kỳ nhắc định kỳ: Hẹn hò cuối tuần, Kỷ niệm mỗi tháng, Ngày sinh nhật.",
                "Tạo lời nhắc tùy chỉnh nhanh chóng: Nhập tên buổi hẹn, thời gian và ghi chú.",
                "Gửi nhanh thông điệp yêu thương hoặc mở quà tặng phù hợp trực tiếp từ lời nhắc.",
                "Đồng bộ với hệ thống thông báo trên điện thoại Android."
              ) else listOf(
                "Enable automated love cadences: Weekend date nights, Monthly anniversaries, Birthdays.",
                "Quickly add custom date reminders with title, scheduled date, and romantic notes.",
                "Quick actions to send love messages or open relevant gift suggestions.",
                "Seamlessly synchronizes with Android device notification system."
              )
            )
            4 -> GuideContentSection(
              title = if (language == AppLanguage.VI) "5. Cài Đặt, Đổi Nền & Bảo Mật" else "5. Settings, Wallpapers & Privacy",
              desc = if (language == AppLanguage.VI)
                "Cá nhân hóa trải nghiệm và bảo vệ những khoảnh khắc riêng tư:"
              else
                "Personalize your experience and safeguard couple memories:",
              points = if (language == AppLanguage.VI) listOf(
                "Đổi hình nền mộng mơ: Hoa anh đào, Hoàng hôn lãng mạn, Đêm sao hoặc dán URL ảnh đôi.",
                "Chụp ảnh / Lưu giữ kỷ niệm với ghi chú cảm xúc vào dòng thời gian.",
                "Chuyển đổi ngôn ngữ Tiếng Việt 🇻🇳 và English 🇬🇧 bất kỳ lúc nào.",
                "Quản lý hồ sơ Room Database và bảo mật vân tay cho ứng dụng."
              ) else listOf(
                "Change dreamy wallpapers: Cherry Blossom, Sunset, Starry Night, or custom photo URL.",
                "Capture and record romantic photo memories with captions onto the timeline.",
                "Switch languages between Tiếng Việt 🇻🇳 and English 🇬🇧 at any time.",
                "Manage local Room SQLite database and privacy app lock."
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Confirm Button
        Button(
          onClick = onDismiss,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("btn_close_guide")
        ) {
          Text(
            text = strings.btnGotIt,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }
    }
  }
}

@Composable
private fun GuideContentSection(
  title: String,
  desc: String,
  points: List<String>
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F9)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFFFFCDD2).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = title,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Primary
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = desc,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF6B2B50)
      )

      Spacer(modifier = Modifier.height(10.dp))

      points.forEach { point ->
        Row(
          modifier = Modifier.padding(vertical = 3.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = HotPink,
            modifier = Modifier
              .size(16.dp)
              .padding(top = 2.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = point,
            fontSize = 12.sp,
            color = Color(0xFF26071B),
            lineHeight = 17.sp
          )
        }
      }
    }
  }
}

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

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    uri?.let {
      val savedPath = copyUriToInternalStorage(context, it)
      selectedUrl = savedPath ?: it.toString()
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

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 14.dp,
      modifier = Modifier
        .fillMaxWidth()
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
            Column {
              Text(
                text = strings.wallpaperTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26071B)
              )
              Text(
                text = strings.wallpaperSubtitle,
                fontSize = 11.sp,
                color = Color(0xFF6B2B50)
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF6B2B50))
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Button: Upload from Device (Primary custom option per user request)
        Button(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("btn_upload_wallpaper_device")
        ) {
          Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (language == AppLanguage.VI) "🖼️ Tải ảnh từ thư viện máy" else "🖼️ Choose Photo from Device",
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = Color.White
          )
        }

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
        Button(
          onClick = {
            onApplyWallpaper(selectedUrl)
            onDismiss()
          },
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("btn_apply_wallpaper")
        ) {
          Text(
            text = strings.btnApplyWallpaper,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
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
    color = Color.White,
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
          .background(Color.Black.copy(alpha = 0.35f))
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

// ==========================================
// 4. CAPTURE & SAVE MEMORY PHOTO DIALOG
// ==========================================
@Composable
fun CaptureMemoryDialog(
  language: AppLanguage,
  presetPhotos: List<String> = emptyList(),
  onSaveMemory: (note: String, photoUrl: String) -> Unit,
  onDismiss: () -> Unit
) {
  val strings = LocalizedStrings.get(language)
  var noteText by remember { mutableStateOf("") }
  var photoUrlText by remember(presetPhotos) {
    mutableStateOf(presetPhotos.firstOrNull() ?: "")
  }
  var isSaving by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White.copy(alpha = 0.98f),
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
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
      ) {
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
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = strings.memoryTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26071B)
              )
              Text(
                text = strings.memorySubtitle,
                fontSize = 11.sp,
                color = Color(0xFF6B2B50)
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF6B2B50))
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Image Preview Thumbnail
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFFFCDD2), RoundedCornerShape(18.dp))
        ) {
          AsyncImage(
            model = photoUrlText,
            contentDescription = "Memory Preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(8.dp)
          ) {
            Text(
              text = if (language == AppLanguage.VI) "Xem trước ảnh 📸" else "Photo Preview 📸",
              color = Color.White,
              fontSize = 10.sp,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Image URL Input
        Text(
          text = if (language == AppLanguage.VI) "Liên kết ảnh kỷ niệm:" else "Photo URL:",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF26071B)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = photoUrlText,
          onValueChange = { photoUrlText = it },
          placeholder = { Text(strings.memoryUrlPlaceholder, fontSize = 12.sp) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color(0xFFFFCDD2)
          )
        )

        if (presetPhotos.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (language == AppLanguage.VI) "Hoặc chọn từ bộ sưu tập mẫu:" else "Or pick from curated presets:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary
          )
          Spacer(modifier = Modifier.height(4.dp))
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(presetPhotos) { url ->
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .border(
                    width = if (photoUrlText == url) 2.dp else 1.dp,
                    color = if (photoUrlText == url) Primary else Color(0xFFFFCDD2),
                    shape = RoundedCornerShape(8.dp)
                  )
                  .clickable { photoUrlText = url }
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

        Spacer(modifier = Modifier.height(10.dp))

        // Note Input
        Text(
          text = if (language == AppLanguage.VI) "Lời nhắn gửi / Cảm xúc:" else "Sweet note / Feeling:",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF26071B)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = noteText,
          onValueChange = { noteText = it },
          placeholder = { Text(strings.memoryNotePlaceholder, fontSize = 12.sp) },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color(0xFFFFCDD2)
          )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button — isSaving guards against a rapid double-tap firing onSaveMemory twice
        // before the dialog closes (it closes itself asynchronously, from the ViewModel side).
        Button(
          onClick = {
            isSaving = true
            onSaveMemory(noteText, photoUrlText)
          },
          enabled = !isSaving,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("btn_confirm_save_memory")
        ) {
          if (isSaving) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = strings.btnSaveMemory,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }
    }
  }
}
