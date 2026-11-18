@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.dossier.SecretHintNote
import com.example.shared.monetization.ProTierCatalog
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.InLoveStatusPill
import com.example.ui.designsystem.InLoveTextField
import com.example.ui.designsystem.InLoveTintedCard
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
internal fun PartnerDossierSecretNotesBlock(
  notes: List<SecretHintNote>,
  isVip: Boolean,
  isEnglish: Boolean,
  onAddNote: (noteText: String, occasionHint: String) -> Unit,
  onTogglePurchased: (noteId: String, purchased: Boolean) -> Unit,
  onRemoveNote: (noteId: String) -> Unit,
  onOpenPaywall: () -> Unit,
) {
  var noteText by remember { mutableStateOf("") }
  var occasionHint by remember { mutableStateOf("") }
  val atFreeLimit = !isVip && notes.size >= ProTierCatalog.FREE_SECRET_NOTES_LIMIT

  InLoveTintedCard(
    containerColor = InLoveColors.LavenderCardBg,
    borderColor = InLoveColors.LavenderBorder
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = if (isEnglish) "Private Secret Surprise Notes" else "Ghi Chú Bí Mật (Private)",
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextDarkPlum
      )
      InLoveStatusPill(
        text = if (isVip) "Pro ∞" else "${notes.size}/${ProTierCatalog.FREE_SECRET_NOTES_LIMIT}",
        containerColor = if (atFreeLimit) InLoveColors.WarningBg else InLoveColors.RoseTint,
        contentColor = if (atFreeLimit) InLoveColors.WarningText else Primary,
        borderColor = if (atFreeLimit) InLoveColors.WarningBorder else InLoveColors.BorderDelicate
      )
    }

    Text(
      text = if (isEnglish) {
        "Jot down hints your partner casually mentions for future surprises."
      } else {
        "Ghi nhanh những món người ấy vô tình nhắc để tạo bất ngờ đúng lúc."
      },
      fontSize = 11.5.sp,
      color = TextSubtlePlum
    )

    notes.forEach { item ->
      Surface(
        shape = RoundedCornerShape(InLoveRadii.Input),
        color = Color.White,
        border = BorderStroke(1.dp, InLoveColors.BorderRose),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { onTogglePurchased(item.id, !item.isPurchased) },
            modifier = Modifier.size(MinTouchBox)
          ) {
            Icon(
              imageVector = if (item.isPurchased) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
              contentDescription = if (isEnglish) "Purchased" else "Đã mua",
              tint = if (item.isPurchased) Primary else TextSubtlePlum
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.note,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextDarkPlum,
              textDecoration = if (item.isPurchased) TextDecoration.LineThrough else null
            )
            if (item.occasionHint.isNotBlank()) {
              Text(
                text = item.occasionHint,
                fontSize = 11.sp,
                color = TextSubtlePlum
              )
            }
          }
          IconButton(
            onClick = { onRemoveNote(item.id) },
            modifier = Modifier.size(MinTouchBox)
          ) {
            Icon(
              imageVector = Icons.Filled.Delete,
              contentDescription = if (isEnglish) "Delete note" else "Xóa ghi chú",
              tint = TextSubtlePlum
            )
          }
        }
      }
    }

    if (atFreeLimit) {
      SecondaryButton(
        text = if (isEnglish) "Unlock Unlimited Secret Notes (Pro)" else "Mở khóa Ghi Chú Bí Mật Không Giới Hạn (Pro)",
        icon = Icons.Filled.WorkspacePremium,
        onClick = onOpenPaywall,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox),
        testTag = "btn_secret_notes_upgrade"
      )
    } else {
      InLoveTextField(
        value = noteText,
        onValueChange = { noteText = it.take(180) },
        label = if (isEnglish) "New surprise hint" else "Gợi ý bất ngờ mới",
        placeholder = if (isEnglish) "e.g. Mentioned Jo Malone Peony candle" else "VD: Thích nến thơm mùi hoa mẫu đơn",
        modifier = Modifier.fillMaxWidth(),
        testTag = "input_secret_note_text"
      )
      InLoveTextField(
        value = occasionHint,
        onValueChange = { occasionHint = it.take(80) },
        label = if (isEnglish) "Occasion / shop hint (optional)" else "Dịp / nơi bán gợi ý (tùy chọn)",
        modifier = Modifier.fillMaxWidth()
      )
      SecondaryButton(
        text = if (isEnglish) "Add Secret Note" else "Thêm Ghi Chú Bí Mật",
        icon = Icons.Filled.Add,
        onClick = {
          val clean = noteText.trim()
          if (clean.isNotEmpty()) {
            onAddNote(clean, occasionHint.trim())
            noteText = ""
            occasionHint = ""
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox),
        testTag = "btn_add_secret_note"
      )
    }
  }
}
