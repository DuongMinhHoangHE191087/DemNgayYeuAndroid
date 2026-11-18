@file:Suppress("FunctionName")
package com.example.ui.dialogs

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SecondaryButton
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Secondary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.ProfileUtils
import com.example.ui.util.copyCoupleCode
import com.example.ui.util.displayPartnerName
import com.example.ui.util.shareCoupleCode
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CouplePairingTabContent(
  context: Context,
  viewModel: InLoveViewModel,
  currentUser: OnlineUserEntity,
  partnerUser: OnlineUserEntity?,
  relationshipStatus: String,
  incomingInvite: OnlineInviteEntity?,
  outgoingInvite: OnlineInviteEntity?,
  girlName: String,
  girlAvatarUrl: String,
  todayFormatted: String,
  anniversaryDate: String,
  isEnglish: Boolean,
  onDismiss: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var searchError by remember { mutableStateOf<String?>(null) }
  var selectedPartner by remember { mutableStateOf<OnlineUserEntity?>(null) }
  var proposedStartDateText by remember { mutableStateOf(anniversaryDate.ifEmpty { todayFormatted }) }
  var loveNote by remember { mutableStateOf("") }

  fun copyCodeToClipboard(text: String) {
    copyCoupleCode(context, text)
    viewModel.showToast(if (isEnglish) "Copied: $text" else "Đã sao chép: $text")
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    incomingInvite?.let { invite ->
      IncomingInviteCard(
        invite = invite,
        isEnglish = isEnglish,
        onDecline = { viewModel.rejectSetLoveInvite(invite.inviteId) },
        onAccept = {
          viewModel.acceptSetLoveInvite(invite.inviteId)
          onDismiss()
        }
      )
    }

    outgoingInvite?.takeIf { relationshipStatus != OnlineStatus.COUPLED }?.let { out ->
      Card(
        shape = RoundedCornerShape(InLoveRadii.Chip),
        colors = CardDefaults.cardColors(containerColor = InLoveColors.WarningBg),
        border = BorderStroke(1.2.dp, InLoveColors.WarningBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            if (isEnglish) "Waiting for response..." else "Chờ xác nhận...",
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = InLoveColors.WarningText
          )
          val outDays = ProfileUtils.calculateLoveDays(out.proposedStartDate)
          Text(
            text = if (isEnglish) "Code: ${out.targetCoupleCode} • $outDays days" else "Mã: ${out.targetCoupleCode} • $outDays ngày",
            fontSize = 12.5.sp,
            color = TextDarkPlum
          )
          SecondaryButton(
            text = if (isEnglish) "Cancel request" else "Hủy lời mời",
            onClick = { viewModel.cancelSentInvite() },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = MinTouchBox)
          )
        }
      }
    }

    if (relationshipStatus == OnlineStatus.COUPLED) {
      Card(
        shape = RoundedCornerShape(InLoveRadii.Chip),
        colors = CardDefaults.cardColors(containerColor = InLoveColors.RoseTint),
        border = BorderStroke(1.2.dp, Color(0xFFFF80AB)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            if (isEnglish) "Coupled ❤️" else "Đã kết đôi ❤️",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = InLoveColors.RoseHighlight
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(
              url = partnerUser?.avatarUrl.orEmpty().ifBlank { girlAvatarUrl },
              contentDescription = null,
              modifier = Modifier
                .size(44.dp)
                .border(2.dp, Secondary, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                displayPartnerName(partnerUser?.effectiveDisplayName.orEmpty().ifBlank { girlName }, isEnglish),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextDarkPlum
              )
              Text(
                if (isEnglish) "Code: ${partnerUser?.coupleCode ?: ""}" else "Mã: ${partnerUser?.coupleCode ?: ""}",
                fontSize = 12.sp,
                color = TextSubtlePlum
              )
            }
          }
        }
      }
    }

    if (relationshipStatus != OnlineStatus.COUPLED && incomingInvite == null) {
      YourCoupleCodeCard(
        coupleCode = currentUser.coupleCode,
        isEnglish = isEnglish,
        onCopy = { copyCodeToClipboard(currentUser.coupleCode) },
        onShare = { shareCoupleCode(context, currentUser.coupleCode, isEnglish) }
      )

      if (outgoingInvite == null) {
        val runSearch: () -> Unit = {
          val q = searchQuery.trim()
          if (q.isNotBlank() && !isSearching) {
            coroutineScope.launch {
              isSearching = true
              searchError = null
              val res = try {
                viewModel.searchPartnerForSetLove(q)
              } catch (e: CancellationException) {
                throw e
              } catch (_: Exception) {
                null
              } finally {
                isSearching = false
              }
              if (q != searchQuery.trim()) return@launch
              when {
                res == null -> searchError = if (isEnglish) "No user found with this code" else "Không tìm thấy người dùng với mã này"
                res.uid == currentUser.uid -> searchError = if (isEnglish) "You can't send a request to yourself" else "Không thể gửi lời mời cho chính mình"
                else -> selectedPartner = res
              }
            }
          }
        }

        ConnectSearchCard(
          context = context,
          searchQuery = searchQuery,
          isSearching = isSearching,
          searchError = searchError,
          selectedPartner = selectedPartner,
          proposedStartDateText = proposedStartDateText,
          loveNote = loveNote,
          isEnglish = isEnglish,
          onQueryChange = {
            searchQuery = it
            searchError = null
            selectedPartner = null
          },
          onEmptyClipboard = {
            viewModel.showToast(if (isEnglish) "Clipboard is empty" else "Bộ nhớ tạm trống")
          },
          onSearch = runSearch,
          onDateChange = { proposedStartDateText = it },
          onNoteChange = { loveNote = it },
          onSendInvite = { partner ->
            val parsedMillis = ProfileUtils.parseDateToMillis(proposedStartDateText)
            viewModel.sendSetLoveInvite(
              targetCodeOrLink = partner.coupleCode,
              proposedStartDateMillis = parsedMillis,
              loveNote = loveNote
            )
            onDismiss()
          }
        )
      }
    }

    SecondaryButton(
      text = if (isEnglish) "Close" else "Đóng",
      onClick = onDismiss,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox),
      testTag = "btn_close_pairing_tab"
    )
  }
}
