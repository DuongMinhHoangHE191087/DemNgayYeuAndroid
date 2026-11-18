@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase
import com.example.domain.usecase.PersonalizedGiftSuggestion
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.disabledLook
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
fun PersonalizedGiftCardContent(
  title: String,
  reason: String,
  preparation: String,
  priceLabel: String?,
  modifier: Modifier = Modifier,
  onShare: (() -> Unit)? = null,
  shareLabel: String = "",
  onScheduleReminder: (() -> Unit)? = null,
  reminderLabel: String = "",
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color.White,
    border = BorderStroke(1.dp, InLoveColors.BorderRose),
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable { expanded = !expanded }
      .animateContentSize()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Favorite,
          contentDescription = null,
          tint = Secondary,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = title,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDarkPlum,
          modifier = Modifier.weight(1f)
        )
        Icon(
          imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = null,
          tint = TextSubtlePlum,
          modifier = Modifier.size(18.dp)
        )
        if (onShare != null) {
          IconButton(
            onClick = onShare,
            modifier = Modifier
              .size(MinTouchBox)
              .testTag("btn_share_gift_suggestion")
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = shareLabel,
              tint = InLoveColors.RoseHighlight,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
      if (priceLabel != null) {
        Text(
          text = priceLabel,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Medium,
          color = Primary,
          maxLines = 1
        )
      }
      if (expanded) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = reason,
          fontSize = 12.sp,
          color = TextDarkPlum,
          lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = preparation,
          fontSize = 11.5.sp,
          color = TextSubtlePlum,
          lineHeight = 16.sp
        )
        if (onScheduleReminder != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            shape = RoundedCornerShape(InLoveRadii.Pill),
            color = InLoveColors.RoseTint,
            border = BorderStroke(1.dp, InLoveColors.BorderDelicate),
            modifier = Modifier
              .heightIn(min = MinTouchBox)
              .clip(RoundedCornerShape(InLoveRadii.Pill))
              .clickable(onClick = onScheduleReminder)
              .testTag("btn_remind_gift_suggestion")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.AlarmAdd,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = reminderLabel.ifBlank { "Nhắc chuẩn bị quà" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun PersonalizedGiftListContent(
  suggestions: List<PersonalizedGiftSuggestion>,
  shareLabel: String,
  onShare: (String) -> Unit,
  onScheduleReminder: ((PersonalizedGiftSuggestion) -> Unit)? = null,
  reminderLabel: String = "",
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    suggestions.forEach { s ->
      key(s.idea.remoteId) {
        PersonalizedGiftCardContent(
          title = s.gift.title,
          reason = s.reason,
          preparation = s.preparation,
          priceLabel = s.priceLabel,
          onShare = { onShare(listOfNotNull(s.gift.title, s.priceLabel).joinToString("\n")) },
          shareLabel = shareLabel,
          onScheduleReminder = onScheduleReminder?.let { cb -> { cb(s) } },
          reminderLabel = reminderLabel
        )
      }
    }
  }
}

@Composable
fun PartnerPreferencesEditorContent(
  profileExists: Boolean,
  savedLikes: String,
  savedBudgetVnd: Long,
  savedRegion: String,
  isEnglish: Boolean,
  onSave: (String, Long, String) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  var likes by remember(savedLikes) { mutableStateOf(savedLikes) }
  var budgetText by remember(savedBudgetVnd) {
    mutableStateOf(if (savedBudgetVnd > 0L) savedBudgetVnd.toString() else "")
  }
  var region by remember(savedRegion) { mutableStateOf(savedRegion) }

  Column(modifier = Modifier.fillMaxWidth()) {
    TextButton(
      onClick = { expanded = !expanded },
      colors = ButtonDefaults.textButtonColors(contentColor = Primary),
      modifier = Modifier
        .heightIn(min = MinTouchBox)
        .testTag("btn_toggle_partner_preferences")
    ) {
      Text(
        text = if (isEnglish) "Likes & budget" else "Sở thích & ngân sách",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
      )
    }
    if (expanded) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = likes,
          onValueChange = { likes = it.take(200) },
          label = { Text(if (isEnglish) "What your partner likes" else "Điều đối tác thích") },
          placeholder = { Text(if (isEnglish) "coffee, travel, books" else "cà phê, du lịch, sách") },
          singleLine = true,
          shape = RoundedCornerShape(InLoveRadii.Input),
          colors = inLoveOutlinedTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_likes_field")
        )
        OutlinedTextField(
          value = budgetText,
          onValueChange = { budgetText = it.filter(Char::isDigit).take(12) },
          label = { Text(if (isEnglish) "Gift budget (VND)" else "Ngân sách quà (VND)") },
          supportingText = { Text(if (isEnglish) "0 = no limit" else "0 = không giới hạn", color = TextSubtlePlum) },
          singleLine = true,
          shape = RoundedCornerShape(InLoveRadii.Input),
          colors = inLoveOutlinedTextFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_budget_field")
        )
        Text(
          text = if (isEnglish) "Holiday region" else "Vùng dịp lễ",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = TextSubtlePlum
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            Triple(GetPersonalizedGiftSuggestionsUseCase.REGION_VN, if (isEnglish) "Vietnam" else "Việt Nam", "partner_region_vn"),
            Triple(GetPersonalizedGiftSuggestionsUseCase.REGION_INTL, if (isEnglish) "International" else "Quốc tế", "partner_region_intl")
          ).forEach { (key, label, tag) ->
            val isSelected = region == key
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
              modifier = Modifier
                .widthIn(min = MinTouchBox)
                .heightIn(min = MinTouchBox)
                .clip(RoundedCornerShape(InLoveRadii.Chip))
                .background(if (isSelected) Color(0xFFC2185B) else InLoveColors.ScreenSurface)
                .clickable { region = if (isSelected) "" else key }
                .animateContentSize()
                .padding(horizontal = 12.dp)
                .testTag(tag)
            ) {
              Icon(
                imageVector = if (key == GetPersonalizedGiftSuggestionsUseCase.REGION_VN) Icons.Filled.Flag else Icons.Filled.Public,
                contentDescription = label,
                tint = if (isSelected) Color.White else TextSubtlePlum,
                modifier = Modifier.size(18.dp)
              )
              if (isSelected) {
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
              }
            }
          }
        }
        if (!profileExists) {
          Text(
            text = if (isEnglish) "Create your couple profile first to save." else "Tạo hồ sơ cặp đôi trước để lưu.",
            fontSize = 11.5.sp,
            color = TextSubtlePlum
          )
        }
        PrimaryGradientButton(
          text = if (isEnglish) "Save" else "Lưu",
          onClick = {
            if (profileExists) {
              onSave(likes.trim(), budgetText.toLongOrNull() ?: 0L, region)
              expanded = false
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchBox)
            .disabledLook(profileExists),
          testTag = "btn_save_partner_preferences"
        )
      }
    }
  }
}
