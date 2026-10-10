package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.UnlockResult
import com.example.ui.util.AppLanguage
import com.example.ui.util.findActivity
import com.example.ui.util.unlockFailureText
import kotlinx.coroutines.delay
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.UserAccountEntity
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.launch

@Composable
fun AppPinLockScreen(
  account: UserAccountEntity,
  viewModel: InLoveViewModel,
  modifier: Modifier = Modifier
) {
  val scope = rememberCoroutineScope()
  var enteredPin by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showPasswordFallbackDialog by remember { mutableStateOf(false) }
  val isEnglish = viewModel.appLanguage.collectAsState().value == AppLanguage.EN
  val context = LocalContext.current
  val canUseFacebook = remember(account.uid) { viewModel.authRepo.isFacebookLinked }
  var isBusy by remember { mutableStateOf(false) }

  // Persisted brute-force lock: survives process death, counts down on screen.
  var lockedUntil by remember { mutableLongStateOf(viewModel.authRepo.lockedUntilMillis()) }
  var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(lockedUntil) {
    while (System.currentTimeMillis() < lockedUntil) {
      nowMs = System.currentTimeMillis()
      delay(500)
    }
    nowMs = System.currentTimeMillis()
  }
  val lockSeconds = ((lockedUntil - nowMs + 999) / 1000).coerceAtLeast(0)
  val isLocked = lockSeconds > 0

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(
            Color(0xFFFFF0F5),
            Color(0xFFFFFAF0),
            Color(0xFFFFF5F7)
          )
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 28.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top header
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 24.dp)
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .shadow(8.dp, CircleShape)
            .background(
              Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFE91E63))),
              CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Khóa Riêng Tư InLove",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFFC2185B)
        )

        Text(
          text = if (isEnglish) "Hi ${account.displayName}, enter your 4-digit PIN" else "Xin chào ${account.displayName}, vui lòng nhập mã PIN 4 số",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF757575),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4 PIN Dots Indicator
        Row(
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (i in 0 until 4) {
            val isFilled = i < enteredPin.length
            Box(
              modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                  if (isFilled) Color(0xFFE91E63) else Color.Transparent
                )
                .border(
                  width = 2.dp,
                  color = if (isFilled) Color(0xFFE91E63) else Color(0xFFBDBDBD),
                  shape = CircleShape
                )
            )
          }
        }

        if (isLocked) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (isEnglish) "Locked for ${lockSeconds}s after too many wrong tries" else "Đã khóa tạm ${lockSeconds}s do nhập sai nhiều lần",
            color = Color(0xFFD32F2F),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        } else if (errorMessage != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = errorMessage.orEmpty(),
            color = Color(0xFFD32F2F),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      // Numeric Keypad
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        val rows = listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
          listOf("lock", "0", "back")
        )

        rows.forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            row.forEach { key ->
              when (key) {
                "lock" -> {
                  Box(
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .clickable { showPasswordFallbackDialog = true },
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Favorite,
                      contentDescription = "Mật khẩu",
                      tint = Color(0xFFE91E63),
                      modifier = Modifier.size(24.dp)
                    )
                  }
                }
                "back" -> {
                  Box(
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .clickable {
                        if (enteredPin.isNotEmpty()) {
                          enteredPin = enteredPin.dropLast(1)
                          errorMessage = null
                        }
                      },
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Backspace,
                      contentDescription = "Xóa",
                      tint = Color(0xFF616161),
                      modifier = Modifier.size(26.dp)
                    )
                  }
                }
                else -> {
                  Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .clickable {
                        if (enteredPin.length < 4 && !isLocked && !isBusy) {
                          val nextPin = enteredPin + key
                          enteredPin = nextPin
                          errorMessage = null
                          if (nextPin.length == 4) {
                            isBusy = true
                            scope.launch {
                              val result = viewModel.authRepo.unlockWithPin(nextPin)
                              isBusy = false
                              if (result !is UnlockResult.Success) {
                                errorMessage = unlockFailureText(result, isEnglish)
                                if (result is UnlockResult.Locked) lockedUntil = result.untilMillis
                                enteredPin = ""
                              }
                            }
                          }
                        }
                      }
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = key,
                        style = MaterialTheme.typography.titleLarge.copy(
                          fontWeight = FontWeight.Bold,
                          fontSize = 24.sp
                        ),
                        color = Color(0xFF333333)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Bottom actions
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 12.dp)
      ) {
        if (canUseFacebook) {
          Button(
            onClick = {
              val activity = context.findActivity() ?: return@Button
              isBusy = true
              scope.launch {
                val result = viewModel.authRepo.unlockWithFacebook(activity)
                isBusy = false
                if (result !is UnlockResult.Success) viewModel.showToast(unlockFailureText(result, isEnglish).orEmpty())
              }
            },
            enabled = !isBusy,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("btn_pin_use_facebook")
          ) {
            Text(
              text = if (isEnglish) "Unlock with Facebook" else "Mở khóa bằng Facebook",
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        if (!viewModel.authRepo.isProviderOnly(account)) TextButton(
          onClick = { showPasswordFallbackDialog = true },
          modifier = Modifier.testTag("btn_pin_use_password")
        ) {
          Text(
            text = "Quên PIN? Mở khóa bằng mật khẩu tài khoản",
            color = Color(0xFFC2185B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          )
        }

        TextButton(
          onClick = {
            scope.launch {
              viewModel.authRepo.logout()
              viewModel.showToast("Đã đăng xuất tài khoản!")
            }
          }
        ) {
          Text(
            text = "Đăng xuất tài khoản khác",
            color = Color(0xFF757575),
            fontSize = 12.sp
          )
        }
      }
    }
  }

  // Fallback Password Dialog
  if (showPasswordFallbackDialog && !viewModel.authRepo.isProviderOnly(account)) {
    var passwordInput by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { showPasswordFallbackDialog = false }) {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
          .fillMaxWidth(0.92f)
          .padding(16.dp)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "Xác Thực Mật Khẩu",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Color(0xFF212121)
          )
          Text(
            text = "Nhập mật khẩu tài khoản để mở khóa ứng dụng",
            fontSize = 12.sp,
            color = Color(0xFF757575),
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
          )

          OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text("Mật khẩu tài khoản") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )

          if (pwdError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = pwdError.orEmpty(), color = Color(0xFFD32F2F), fontSize = 12.sp)
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = { showPasswordFallbackDialog = false },
              modifier = Modifier.weight(1f)
            ) {
              Text("Đóng")
            }

            androidx.compose.material3.Button(
              onClick = {
                scope.launch {
                  val result = viewModel.authRepo.unlockWithAccountPassword(passwordInput)
                  if (result is UnlockResult.Success) {
                    showPasswordFallbackDialog = false
                  } else {
                    pwdError = unlockFailureText(result, isEnglish, "password")
                    if (result is UnlockResult.Locked) lockedUntil = result.untilMillis
                  }
                }
              },
              colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
              modifier = Modifier.weight(1f)
            ) {
              Text("Mở khóa")
            }
          }
        }
      }
    }
  }
}
