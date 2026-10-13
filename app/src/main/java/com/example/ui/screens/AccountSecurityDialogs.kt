package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.ui.components.animateDialogEntrance
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.SecurityAuditLogEntity
import com.example.ui.util.AuthSecurityManager
import com.example.ui.util.PasswordStrengthLevel
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChangePasswordDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val scope = rememberCoroutineScope()
  var oldPassword by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var oldPasswordVisible by remember { mutableStateOf(false) }
  var newPasswordVisible by remember { mutableStateOf(false) }
  var isSubmitting by remember { mutableStateOf(false) }

  val strength = remember(newPassword) {
    AuthSecurityManager.evaluatePasswordStrength(newPassword)
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .animateDialogEntrance()
        .padding(16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .background(Color(0xFFFCE4EC), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = Color(0xFFE91E63),
            modifier = Modifier.size(24.dp)
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Đổi Mật Khẩu",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFF212121)
        )

        Text(
          text = "Cập nhật mật khẩu mới của bạn",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF757575),
          modifier = Modifier.padding(bottom = 14.dp)
        )

        // Old Password
        OutlinedTextField(
          value = oldPassword,
          onValueChange = { oldPassword = it },
          label = { Text("Mật khẩu hiện tại") },
          visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
              Icon(
                imageVector = if (oldPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null
              )
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // New Password
        OutlinedTextField(
          value = newPassword,
          onValueChange = { newPassword = it },
          label = { Text("Mật khẩu mới") },
          placeholder = { Text("Ít nhất 8 ký tự, chữ hoa, số...") },
          visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
              Icon(
                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null
              )
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        if (newPassword.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { strength.score },
            color = Color(strength.colorHex),
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp))
          )
          Text(
            text = "Độ mạnh: ${strength.label}",
            fontSize = 11.sp,
            color = Color(strength.colorHex),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm Password
        OutlinedTextField(
          value = confirmPassword,
          onValueChange = { confirmPassword = it },
          label = { Text("Xác nhận mật khẩu mới") },
          visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Hủy")
          }

          Button(
            onClick = {
              isSubmitting = true
              scope.launch {
                val res = viewModel.authRepo.changePassword(oldPassword, newPassword, confirmPassword)
                isSubmitting = false
                viewModel.showToast(res.second)
                if (res.first) onDismiss()
              }
            },
            enabled = !isSubmitting && oldPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
            modifier = Modifier.weight(1.4f)
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
            } else {
              Text("Cập Nhật")
            }
          }
        }
      }
    }
  }
}

@Composable
fun SetPinDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  var pinInput by remember { mutableStateOf("") }
  var confirmPinInput by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .padding(16.dp)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .background(Color(0xFFE8F5E9), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(24.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "Cài Đặt Mã PIN 4 Số",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFF212121)
        )

        Text(
          text = "Bảo vệ riêng tư ứng dụng khi mở lại màn hình",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF757575),
          modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
          value = pinInput,
          onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinInput = it },
          label = { Text("Mã PIN (4 số)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = confirmPinInput,
          onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) confirmPinInput = it },
          label = { Text("Xác nhận mã PIN") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        val isValid = pinInput.length == 4 && pinInput == confirmPinInput

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Hủy")
          }

          Button(
            onClick = {
              viewModel.setAppPin(pinInput)
              onDismiss()
            },
            enabled = isValid,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
            modifier = Modifier.weight(1.3f)
          ) {
            Text("Lưu PIN")
          }
        }
      }
    }
  }
}

@Composable
fun SecurityAuditLogsDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val logs by viewModel.authRepo.getAuditLogsForCurrentAccount()
    .collectAsState(initial = emptyList())

  val dateFormat = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .padding(16.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = Color(0xFFE91E63),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Nhật Ký Bảo Mật",
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
              color = Color(0xFF212121)
            )
          }

          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng")
          }
        }

        Text(
          text = "Ghi lại các hoạt động đăng nhập, đổi mật khẩu và bảo mật",
          fontSize = 12.sp,
          color = Color(0xFF757575),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
        )

        if (logs.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Chưa có hoạt động bảo mật nào được ghi nhận",
              color = Color(0xFF9E9E9E),
              fontSize = 13.sp
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(logs) { log ->
              SecurityLogItem(log = log, dateFormat = dateFormat)
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Đóng")
        }
      }
    }
  }
}

@Composable
private fun SecurityLogItem(
  log: SecurityAuditLogEntity,
  dateFormat: SimpleDateFormat
) {
  val (bgColor, iconColor) = when (log.action) {
    "LOGIN_SUCCESS" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
    "LOGIN_FAILED" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
    "LOCKOUT" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
    "REGISTER" -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
    else -> Color(0xFFECEFF1) to Color(0xFF455A64)
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = bgColor.copy(alpha = 0.6f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = when (log.action) {
            "LOGIN_SUCCESS" -> Icons.Default.Check
            "LOGIN_FAILED" -> Icons.Default.Close
            "LOCKOUT" -> Icons.Default.Lock
            else -> Icons.Default.History
          },
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = log.action,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = iconColor
          )
          Text(
            text = dateFormat.format(Date(log.timestamp)),
            fontSize = 10.sp,
            color = Color(0xFF757575)
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = log.detail,
          fontSize = 12.sp,
          color = Color(0xFF37474F)
        )
      }
    }
  }
}
