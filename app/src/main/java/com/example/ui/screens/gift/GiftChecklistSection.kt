@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftReminderEntity
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.OutlineVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
fun ChecklistRowContent(
  item: ChecklistItemEntity,
  isEnglish: Boolean = false,
  onToggle: () -> Unit,
  onEdit: (String) -> Unit,
  onDelete: () -> Unit
) {
  val iconVector = when (item.iconName) {
    "cake" -> Icons.Filled.Cake
    "history_edu" -> Icons.Filled.HistoryEdu
    "table_restaurant" -> Icons.Filled.TableRestaurant
    else -> Icons.Filled.Checklist
  }
  var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
  var showEdit by remember { mutableStateOf(false) }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.White.copy(alpha = 0.85f),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable { expanded = !expanded }
      .testTag("checklist_item_${item.id}")
  ) {
    Column(modifier = Modifier.animateContentSize()) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .size(MinTouchBox)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .testTag("btn_toggle_checklist_${item.id}"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(if (item.isCompleted) Primary else SurfaceContainerHighest),
            contentAlignment = Alignment.Center
          ) {
            if (item.isCompleted) {
              Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = if (isEnglish) "Completed" else "Hoàn thành",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        Text(
          text = item.text,
          fontSize = 13.sp,
          color = if (item.isCompleted) TextSubtlePlum.copy(alpha = 0.65f) else TextDarkPlum,
          textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
          modifier = Modifier.weight(1f)
        )

        Icon(
          imageVector = iconVector,
          contentDescription = null,
          tint = OutlineVariant,
          modifier = Modifier.size(18.dp)
        )
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        RowActionsStrip(
          isEnglish = isEnglish,
          onEdit = { showEdit = true },
          onDelete = onDelete,
          editTag = "btn_edit_checklist_${item.id}",
          deleteTag = "btn_delete_checklist_${item.id}"
        )
      }
    }
  }

  if (showEdit) {
    EditFieldsDialog(
      isEnglish = isEnglish,
      labels = listOf(if (isEnglish) "Task" else "Việc"),
      initial = listOf(item.text),
      onDismiss = { showEdit = false },
      onSave = { values ->
        onEdit(values[0])
        showEdit = false
      }
    )
  }
}

@Composable
fun GiftReminderRowContent(
  item: GiftReminderEntity,
  isEnglish: Boolean = false,
  onToggle: () -> Unit,
  onEdit: (GiftReminderEntity) -> Unit,
  onDelete: () -> Unit
) {
  var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
  var showEdit by remember { mutableStateOf(false) }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (item.isCompleted) SurfaceContainerHighest.copy(alpha = 0.45f) else SurfaceContainerLowest,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable { expanded = !expanded }
      .testTag("gift_reminder_item_${item.id}")
  ) {
    Column(modifier = Modifier.animateContentSize()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onToggle,
          modifier = Modifier
            .size(MinTouchBox)
            .testTag("btn_toggle_gift_reminder_${item.id}")
        ) {
          Icon(
            imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.TaskAlt,
            contentDescription = if (isEnglish) "Completed" else "Hoàn thành",
            tint = if (item.isCompleted) Primary else TextSubtlePlum.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
          )
        }

        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
          Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (item.isCompleted) TextSubtlePlum else TextDarkPlum,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (isEnglish) "To: ${item.recipient}" else "Tặng: ${item.recipient}",
              fontSize = 11.sp,
              color = Primary,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = "• ${item.dueDateText}",
              fontSize = 11.sp,
              color = TextSubtlePlum
            )
            if (item.estimatedBudget.isNotBlank()) {
              Text(
                text = "• ${item.estimatedBudget}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Secondary
              )
            }
          }
          if (item.notes.isNotBlank()) {
            Text(
              text = item.notes,
              fontSize = 11.sp,
              color = TextSubtlePlum,
              maxLines = if (expanded) 4 else 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        RowActionsStrip(
          isEnglish = isEnglish,
          onEdit = { showEdit = true },
          onDelete = onDelete,
          editTag = "btn_edit_gift_reminder_${item.id}",
          deleteTag = "btn_delete_gift_reminder_${item.id}"
        )
      }
    }
  }

  if (showEdit) {
    EditFieldsDialog(
      isEnglish = isEnglish,
      labels = listOf(
        if (isEnglish) "Gift" else "Món quà",
        if (isEnglish) "For" else "Tặng ai",
        if (isEnglish) "Date" else "Ngày",
        if (isEnglish) "Budget" else "Ngân sách",
        if (isEnglish) "Notes" else "Ghi chú"
      ),
      initial = listOf(item.title, item.recipient, item.dueDateText, item.estimatedBudget, item.notes),
      onDismiss = { showEdit = false },
      onSave = { v ->
        onEdit(
          item.copy(
            title = v[0].trim().ifBlank { item.title },
            recipient = v[1].trim().ifBlank { item.recipient },
            dueDateText = v[2].trim().ifBlank { item.dueDateText },
            estimatedBudget = v[3].trim(),
            notes = v[4].trim()
          )
        )
        showEdit = false
      }
    )
  }
}

@Composable
private fun RowActionsStrip(
  isEnglish: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  editTag: String,
  deleteTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onEdit, modifier = Modifier.size(MinTouchBox).testTag(editTag)) {
      Icon(
        imageVector = Icons.Filled.Edit,
        contentDescription = if (isEnglish) "Edit" else "Sửa",
        tint = Primary,
        modifier = Modifier.size(20.dp)
      )
    }
    IconButton(onClick = onDelete, modifier = Modifier.size(MinTouchBox).testTag(deleteTag)) {
      Icon(
        imageVector = Icons.Filled.Delete,
        contentDescription = if (isEnglish) "Delete" else "Xóa",
        tint = TextSubtlePlum,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@Composable
private fun EditFieldsDialog(
  isEnglish: Boolean,
  labels: List<String>,
  initial: List<String>,
  onDismiss: () -> Unit,
  onSave: (List<String>) -> Unit
) {
  val values = remember { mutableStateListOf(*initial.toTypedArray()) }
  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = true,
    modifier = Modifier.animateDialogEntrance(),
    shape = RoundedCornerShape(InLoveRadii.Sheet),
    containerColor = Color.White,
    title = {
      Icon(
        imageVector = Icons.Filled.Edit,
        contentDescription = if (isEnglish) "Edit" else "Sửa",
        tint = Primary
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
          OutlinedTextField(
            value = values[i],
            onValueChange = { values[i] = it },
            label = { Text(label) },
            singleLine = i != labels.lastIndex || labels.size == 1,
            shape = RoundedCornerShape(16.dp),
            colors = inLoveOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    },
    confirmButton = {
      IconButton(
        onClick = { onSave(values.toList()) },
        enabled = values.firstOrNull()?.isNotBlank() == true,
        modifier = Modifier.size(MinTouchBox).testTag("btn_save_gift_edit")
      ) {
        Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = if (isEnglish) "Save" else "Lưu",
          tint = Primary
        )
      }
    },
    dismissButton = {
      IconButton(onClick = onDismiss, modifier = Modifier.size(MinTouchBox)) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = if (isEnglish) "Cancel" else "Hủy",
          tint = TextSubtlePlum
        )
      }
    }
  )
}
