@file:Suppress("FunctionName")
package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineUserEntity
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SecondaryButton
import com.example.ui.components.disabledLook
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.ProfileUtils

@Composable
internal fun IncomingInviteCard(
  invite: OnlineInviteEntity,
  isEnglish: Boolean,
  onDecline: () -> Unit,
  onAccept: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    colors = CardDefaults.cardColors(containerColor = InLoveColors.RoseTint),
    border = BorderStroke(1.5.dp, Secondary),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dialog_incoming_invite_card")
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(
          if (isEnglish) "Pairing request" else "Lời mời ghép đôi",
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = Color(0xFFD81B60)
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(
          url = invite.senderAvatar,
          contentDescription = "Sender Avatar",
          modifier = Modifier
            .size(44.dp)
            .border(2.dp, Color(0xFFFF80AB), CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(invite.effectiveSenderName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = InLoveColors.RoseHighlight)
          Text(
            text = (if (isEnglish) "Code: " else "Mã: ") + invite.senderCoupleCode,
            fontSize = 12.sp,
            color = TextSubtlePlum
          )
        }
      }
      val incomingDateText = invite.proposedStartDateText.ifEmpty { ProfileUtils.formatDate(invite.proposedStartDate) }
      val incomingDays = ProfileUtils.calculateLoveDays(invite.proposedStartDate)
      Surface(
        shape = RoundedCornerShape(InLoveRadii.Input),
        color = Color.White,
        border = BorderStroke(1.dp, InLoveColors.DangerBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(if (isEnglish) "Start date: $incomingDateText" else "Ngày yêu: $incomingDateText", fontSize = 13.sp, color = TextDarkPlum)
          Text(if (isEnglish) "$incomingDays days 💕" else "$incomingDays ngày yêu 💕", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD81B60))
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
          text = if (isEnglish) "Decline" else "Từ chối",
          onClick = onDecline,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox)
        )
        PrimaryGradientButton(
          text = if (isEnglish) "Accept ❤️" else "Đồng ý ❤️",
          onClick = onAccept,
          modifier = Modifier
            .weight(1.2f)
            .heightIn(min = MinTouchBox)
        )
      }
    }
  }
}

@Composable
internal fun YourCoupleCodeCard(
  coupleCode: String,
  isEnglish: Boolean,
  onCopy: () -> Unit,
  onShare: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    colors = CardDefaults.cardColors(containerColor = InLoveColors.RoseTint),
    border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = if (isEnglish) "Your Code" else "Mã của bạn",
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = InLoveColors.RoseHighlight
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White, RoundedCornerShape(InLoveRadii.Input))
          .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = coupleCode,
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFFD81B60),
          letterSpacing = 1.sp
        )
        Row {
          IconButton(onClick = onCopy, modifier = Modifier.size(MinTouchBox)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFFD81B60), modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onShare, modifier = Modifier.size(MinTouchBox)) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFFD81B60), modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
internal fun ConnectSearchCard(
  context: Context,
  searchQuery: String,
  isSearching: Boolean,
  searchError: String?,
  selectedPartner: OnlineUserEntity?,
  proposedStartDateText: String,
  loveNote: String,
  isEnglish: Boolean,
  onQueryChange: (String) -> Unit,
  onEmptyClipboard: () -> Unit,
  onSearch: () -> Unit,
  onDateChange: (String) -> Unit,
  onNoteChange: (String) -> Unit,
  onSendInvite: (OnlineUserEntity) -> Unit,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, InLoveColors.DangerBorder),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(if (isEnglish) "Connect" else "Kết nối", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InLoveColors.RoseHighlight)

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { onQueryChange(it.uppercase()) },
          placeholder = { Text(if (isEnglish) "Enter code..." else "Nhập mã ghép đôi...", fontSize = 13.sp) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearch() }),
          shape = RoundedCornerShape(InLoveRadii.Input),
          colors = inLoveOutlinedTextFieldColors(),
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier
                  .size(MinTouchBox)
                  .testTag("dialog_btn_clear")
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = if (isEnglish) "Clear" else "Xóa",
                  tint = TextSubtlePlum,
                  modifier = Modifier.size(18.dp)
                )
              }
            } else {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .widthIn(min = MinTouchBox)
                  .heightIn(min = MinTouchBox)
                  .testTag("dialog_btn_paste")
                  .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                    if (clipText.isNotEmpty()) {
                      onQueryChange(ProfileUtils.extractCoupleCode(clipText).ifBlank { clipText.uppercase() })
                    } else {
                      onEmptyClipboard()
                    }
                  }
              ) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = InLoveColors.RoseTint,
                  modifier = Modifier.padding(end = 6.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Primary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = if (isEnglish) "Paste" else "Dán mã",
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Primary
                    )
                  }
                }
              }
            }
          },
          modifier = Modifier
            .weight(1f)
            .testTag("dialog_search_input")
        )

        Box(modifier = Modifier.heightIn(min = MinTouchBox), contentAlignment = Alignment.Center) {
          val searchEnabled = searchQuery.isNotBlank() && !isSearching
          PrimaryGradientButton(
            text = "",
            icon = if (isSearching) null else Icons.Default.Search,
            modifier = Modifier
              .heightIn(min = MinTouchBox)
              .disabledLook(searchEnabled)
              .semantics { contentDescription = if (isEnglish) "Search" else "Tìm" },
            testTag = "dialog_btn_search",
            onClick = { if (searchEnabled) onSearch() }
          )
          if (isSearching) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
          }
        }
      }

      if (searchError != null) {
        Text(text = searchError, color = InLoveColors.DangerText, fontSize = 12.sp)
      }

      selectedPartner?.let { partner ->
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = InLoveColors.RoseTint),
          border = BorderStroke(1.dp, Secondary),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              ProfileAvatar(
                url = partner.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                  .size(44.dp)
                  .border(2.dp, Secondary, CircleShape)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(partner.effectiveDisplayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = InLoveColors.RoseHighlight)
                Text(
                  if (isEnglish) "Code: ${partner.coupleCode}" else "Mã: ${partner.coupleCode}",
                  fontSize = 12.sp,
                  color = TextSubtlePlum
                )
              }
            }
            InLoveDatePickerField(
              value = proposedStartDateText,
              onValueChange = onDateChange,
              label = if (isEnglish) "Start date" else "Ngày yêu",
              dialogTitle = if (isEnglish) "Select start date" else "Chọn ngày bắt đầu yêu",
              quickPresets = DatePickerPresets.relationshipStartDatePresets(isEnglish),
              isEnglish = isEnglish,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = loveNote,
              onValueChange = onNoteChange,
              label = { Text(if (isEnglish) "Note (optional)" else "Lời nhắn (tùy chọn)") },
              maxLines = 2,
              shape = RoundedCornerShape(10.dp),
              colors = inLoveOutlinedTextFieldColors(),
              modifier = Modifier.fillMaxWidth()
            )
            PrimaryGradientButton(
              text = if (isEnglish) "Send request ❤️" else "Gửi lời mời ❤️",
              onClick = { onSendInvite(partner) },
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchBox)
            )
          }
        }
      }
    }
  }
}
