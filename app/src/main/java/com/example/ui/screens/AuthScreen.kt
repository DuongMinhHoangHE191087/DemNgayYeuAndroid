@file:Suppress("FunctionName")
package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.util.AuthSecurityManager
import com.example.ui.util.PasswordStrengthLevel
import com.example.ui.viewmodel.InLoveViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color(0xFF1E1E24),
  unfocusedTextColor = Color(0xFF1E1E24),
  focusedContainerColor = Color.White,
  unfocusedContainerColor = Color(0xFFFAFAFA),
  disabledContainerColor = Color(0xFFF5F5F7),
  focusedBorderColor = Color(0xFFE91E63),
  unfocusedBorderColor = Color(0xFFC7C7CC),
  focusedLabelColor = Color(0xFFC2185B),
  unfocusedLabelColor = Color(0xFF424242),
  focusedPlaceholderColor = Color(0xFF757575),
  unfocusedPlaceholderColor = Color(0xFF9E9E9E),
  cursorColor = Color(0xFFE91E63)
)

@Composable
fun AuthScreen(
  viewModel: InLoveViewModel,
  modifier: Modifier = Modifier,
  onBackToGuest: () -> Unit = {}
) {
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Đăng nhập, 1: Đăng ký

  // Sign In Form States
  var loginEmail by remember { mutableStateOf("") }
  var loginPassword by remember { mutableStateOf("") }
  var loginPasswordVisible by remember { mutableStateOf(false) }
  var rememberMe by remember { mutableStateOf(true) }
  var isLoggingIn by remember { mutableStateOf(false) }

  // Sign Up Form States
  var regName by remember { mutableStateOf("") }
  var regEmail by remember { mutableStateOf("") }
  var regPassword by remember { mutableStateOf("") }
  var regConfirmPassword by remember { mutableStateOf("") }
  var regPasswordVisible by remember { mutableStateOf(false) }
  var regConfirmVisible by remember { mutableStateOf(false) }
  var regSecurityQuestion by remember { mutableStateOf(AuthSecurityManager.SECURITY_QUESTIONS[0]) }
  var regQuestionExpanded by remember { mutableStateOf(false) }
  var regSecurityAnswer by remember { mutableStateOf("") }
  var agreeToTerms by remember { mutableStateOf(true) }
  var isRegistering by remember { mutableStateOf(false) }

  // Email Queue OTP States
  var regOtpCode by remember { mutableStateOf("") }
  var isSendingOtp by remember { mutableStateOf(false) }
  var otpCooldownSeconds by remember { mutableIntStateOf(0) }
  var isOtpSent by remember { mutableStateOf(false) }
  var otpNotificationBanner by remember { mutableStateOf<String?>(null) }

  // Listen to Email Queue OTP events for instant emulator/testing visibility
  LaunchedEffect(Unit) {
    viewModel.authRepo.emailQueueService.otpEvents.collect { event ->
      otpNotificationBanner = "💌 Đã gửi mã vào hàng đợi [${event.email}]: ${event.otpCode}"
      if (regEmail.trim().equals(event.email, ignoreCase = true)) {
        regOtpCode = event.otpCode // Auto-fill for seamless testing
      }
    }
  }

  // OTP Cooldown Countdown
  LaunchedEffect(otpCooldownSeconds) {
    if (otpCooldownSeconds > 0) {
      delay(1.seconds)
      otpCooldownSeconds -= 1
    }
  }

  // Lockout & Brute-force local tracker
  var isLockedOut by remember { mutableStateOf(false) }
  var lockoutCountdownSeconds by remember { mutableLongStateOf(0L) }
  var failedAttemptNotice by remember { mutableStateOf<String?>(null) }

  // Forgot password dialog
  var showForgotPasswordDialog by remember { mutableStateOf(false) }

  // Countdown timer for lockout
  LaunchedEffect(isLockedOut, lockoutCountdownSeconds) {
    if (isLockedOut && lockoutCountdownSeconds > 0) {
      delay(1.seconds)
      lockoutCountdownSeconds -= 1
      if (lockoutCountdownSeconds <= 0) {
        isLockedOut = false
        failedAttemptNotice = null
      }
    }
  }

  // Password strength evaluation
  val passwordStrength = remember(regPassword) {
    AuthSecurityManager.evaluatePasswordStrength(regPassword)
  }

  val passwordsMatch = remember(regPassword, regConfirmPassword) {
    regPassword.isNotEmpty() && regPassword == regConfirmPassword
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFFFFF0F5), // Lavender blush
            Color(0xFFFFFAF0), // Floral white
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
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Back to Guest / Offline mode button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = {
            viewModel.closeAuthScreen()
            onBackToGuest()
          },
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Quay lại",
            tint = Color(0xFFC2185B),
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Quay lại chế độ Khách (Offline)",
            color = Color(0xFFC2185B),
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // App Logo & Romantic Branding
      Box(
        modifier = Modifier
          .size(64.dp)
          .shadow(8.dp, CircleShape)
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFFFF4081), Color(0xFFE91E63), Color(0xFFFF80AB))
            ),
            shape = CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Favorite,
          contentDescription = "InLove Logo",
          tint = Color.White,
          modifier = Modifier.size(36.dp)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "InLove",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 1.sp,
          fontSize = 24.sp
        ),
        color = Color(0xFFC2185B)
      )

      Text(
        text = "Đếm ngày yêu thương & Gắn kết trái tim",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = Color(0xFF757575),
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
      )

      // Tab selector: Đăng Nhập / Đăng Ký
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = Color.Transparent,
          contentColor = Color(0xFFE91E63),
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
              color = Color(0xFFE91E63),
              height = 3.dp
            )
          }
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = {
              Text(
                text = "Đăng Nhập",
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
              )
            },
            modifier = Modifier.testTag("tab_login")
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              Text(
                text = "Đăng Ký",
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
              )
            },
            modifier = Modifier.testTag("tab_register")
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Main Card Form Container
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
        ) {
          if (selectedTab == 0) {
            // ==================== TAB 0: ĐĂNG NHẬP ====================
            Spacer(modifier = Modifier.height(6.dp))

            // Lockout Alert Banner
            if (isLockedOut) {
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 14.dp)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(24.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "Tài khoản bị tạm khóa!",
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFC62828),
                      fontSize = 13.sp
                    )
                    Text(
                      text = "Đã nhập sai 5 lần. Vui lòng đợi còn: ${lockoutCountdownSeconds}s",
                      color = Color(0xFFD32F2F),
                      fontSize = 12.sp
                    )
                  }
                }
              }
            } else if (failedAttemptNotice != null) {
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 14.dp)
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = failedAttemptNotice.orEmpty(),
                    color = Color(0xFFE65100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }

            // Input: Email
            OutlinedTextField(
              value = loginEmail,
              onValueChange = { loginEmail = it },
              label = { Text("Địa chỉ Email") },
              placeholder = { Text("vd: ban@gmail.com") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = null,
                  tint = Color(0xFFE91E63)
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
              ),
              keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
              shape = RoundedCornerShape(14.dp),
              colors = authTextFieldColors(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_login_email")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Input: Password
            OutlinedTextField(
              value = loginPassword,
              onValueChange = { loginPassword = it },
              label = { Text("Mật khẩu") },
              placeholder = { Text("Nhập mật khẩu") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = null,
                  tint = Color(0xFFE91E63)
                )
              },
              trailingIcon = {
                IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                  Icon(
                    imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (loginPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                    tint = Color(0xFF757575)
                  )
                }
              },
              visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
              ),
              keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
              shape = RoundedCornerShape(14.dp),
              colors = authTextFieldColors(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_login_password")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Remember Me & Forgot Password Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                  checked = rememberMe,
                  onCheckedChange = { rememberMe = it },
                  colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE91E63))
                )
                Text(
                  text = "Ghi nhớ",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFF616161)
                )
              }

              TextButton(
                onClick = { showForgotPasswordDialog = true },
                modifier = Modifier.testTag("btn_forgot_password")
              ) {
                Text(
                  text = "Quên mật khẩu?",
                  color = Color(0xFFC2185B),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Submit Button: Login
            Button(
              onClick = {
                if (isLockedOut) {
                  viewModel.showToast("Tài khoản đang bị tạm khóa còn ${lockoutCountdownSeconds}s!")
                  return@Button
                }
                focusManager.clearFocus()
                isLoggingIn = true
                scope.launch {
                  val result = viewModel.authRepo.login(loginEmail, loginPassword, rememberMe)
                  isLoggingIn = false
                  if (!result.first) {
                    val msg = result.second
                    viewModel.showToast(msg)
                    if (msg.contains("tạm khóa") || msg.contains("5 lần")) {
                      isLockedOut = true
                      lockoutCountdownSeconds = 180L
                    } else if (msg.contains("lần thử")) {
                      failedAttemptNotice = msg
                    }
                  } else {
                    viewModel.showToast(result.second)
                    viewModel.closeAuthScreen()
                    onBackToGuest()
                  }
                }
              },
              enabled = !isLoggingIn && !isLockedOut && loginEmail.isNotBlank() && loginPassword.isNotBlank(),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE91E63),
                disabledContainerColor = Color(0xFFF8BBD0)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_submit_login")
            ) {
              if (isLoggingIn) {
                CircularProgressIndicator(
                  color = Color.White,
                  modifier = Modifier.size(24.dp),
                  strokeWidth = 2.dp
                )
              } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Đăng Nhập",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fast Test Login for QA / Developers (Uses Cloud Firestore Fixtures)
            Text(
              text = "Tài khoản kiểm thử nhanh (Cloud Fixtures):",
              fontSize = 11.5.sp,
              color = Color.Gray,
              fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  isLoggingIn = true
                  scope.launch {
                    val res = viewModel.authRepo.loginTestUser(isPartner = false)
                    isLoggingIn = false
                    viewModel.showToast(res.second)
                    if (res.first) {
                      viewModel.closeAuthScreen()
                      onBackToGuest()
                    }
                  }
                },
                enabled = !isLoggingIn,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("btn_quick_login_tester_a")
              ) {
                Text("Tester A 👨", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }

              OutlinedButton(
                onClick = {
                  isLoggingIn = true
                  scope.launch {
                    val res = viewModel.authRepo.loginTestUser(isPartner = true)
                    isLoggingIn = false
                    viewModel.showToast(res.second)
                    if (res.first) {
                      viewModel.closeAuthScreen()
                      onBackToGuest()
                    }
                  }
                },
                enabled = !isLoggingIn,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("btn_quick_login_tester_b")
              ) {
                Text("Tester B 👩", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          } else {
            // ==================== TAB 1: ĐĂNG KÝ ====================
            Spacer(modifier = Modifier.height(10.dp))

            // Input: Display Name
            OutlinedTextField(
              value = regName,
              onValueChange = { regName = it },
              label = { Text("Tên hiển thị / Biệt danh") },
              placeholder = { Text("Nhập họ tên hoặc biệt danh...") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = null,
                  tint = Color(0xFFE91E63)
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
              keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
              shape = RoundedCornerShape(14.dp),
              colors = authTextFieldColors(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_reg_name")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Input: Email
            OutlinedTextField(
              value = regEmail,
              onValueChange = { regEmail = it },
              label = { Text("Địa chỉ Email") },
              placeholder = { Text("email@domain.com") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = null,
                  tint = Color(0xFFE91E63)
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
              ),
              keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
              shape = RoundedCornerShape(14.dp),
              colors = authTextFieldColors(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_reg_email")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Email Verification Queue OTP Section
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Shield,
                      contentDescription = null,
                      tint = Color(0xFFE91E63),
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Xác thực Email (Hàng đợi OTP)",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = Color(0xFF880E4F)
                    )
                  }

                  OutlinedButton(
                    onClick = {
                      if (!AuthSecurityManager.isValidEmail(regEmail)) {
                        viewModel.showToast("Vui lòng nhập địa chỉ email hợp lệ trước khi gửi mã!")
                        return@OutlinedButton
                      }
                      focusManager.clearFocus()
                      isSendingOtp = true
                      scope.launch {
                        val result = viewModel.authRepo.requestRegistrationOtp(regEmail)
                        isSendingOtp = false
                        result.onSuccess { code ->
                          isOtpSent = true
                          otpCooldownSeconds = 60
                          viewModel.showToast("Đã đưa email vào hàng đợi gửi mã OTP! Mã: $code")
                        }.onFailure { err ->
                          viewModel.showToast(err.message ?: "Lỗi gửi mã OTP")
                        }
                      }
                    },
                    enabled = !isSendingOtp && otpCooldownSeconds == 0 && regEmail.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE91E63)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                  ) {
                    if (isSendingOtp) {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color(0xFFE91E63))
                    } else {
                      Text(
                        text = if (otpCooldownSeconds > 0) "Gửi lại (${otpCooldownSeconds}s)" else if (isOtpSent) "Gửi lại mã" else "Gửi mã OTP 📩",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                if (isOtpSent || regOtpCode.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  OutlinedTextField(
                    value = regOtpCode,
                    onValueChange = { if (it.length <= 6) regOtpCode = it.filter { char -> char.isDigit() } },
                    label = { Text("Mã xác thực OTP (6 số)") },
                    placeholder = { Text("Nhập 6 số được gửi qua email") },
                    leadingIcon = {
                      Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = Color(0xFFE91E63))
                    },
                    trailingIcon = {
                      if (regOtpCode.length == 6) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047))
                      }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(10.dp),
                    colors = authTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_otp")
                  )
                }

                if (otpNotificationBanner != null) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = otpNotificationBanner!!,
                    fontSize = 10.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input: Password
            OutlinedTextField(
              value = regPassword,
              onValueChange = { regPassword = it },
              label = { Text("Mật khẩu") },
              placeholder = { Text("Nhập mật khẩu (tối thiểu 6 ký tự)") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = null,
                  tint = Color(0xFFE91E63)
                )
              },
              trailingIcon = {
                IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                  Icon(
                    imageVector = if (regPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                  )
                }
              },
              visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
              ),
              keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
              shape = RoundedCornerShape(14.dp),
              colors = authTextFieldColors(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_reg_password")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Input: Confirm Password
            OutlinedTextField(
              value = regConfirmPassword,
              onValueChange = { regConfirmPassword = it },
              label = { Text("Xác nhận mật khẩu") },
              placeholder = { Text("Nhập lại chính xác mật khẩu") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Key,
                  contentDescription = null,
                  tint = Color(0xFFE91E63)
                )
              },
              trailingIcon = {
                IconButton(onClick = { regConfirmVisible = !regConfirmVisible }) {
                  Icon(
                    imageVector = if (regConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                  )
                }
              },
              visualTransformation = if (regConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
              ),
              keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
              shape = RoundedCornerShape(14.dp),
              colors = authTextFieldColors(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_reg_confirm_password")
            )

            if (regConfirmPassword.isNotEmpty()) {
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
              ) {
                Icon(
                  imageVector = if (passwordsMatch) Icons.Default.CheckCircle else Icons.Default.Close,
                  contentDescription = null,
                  tint = if (passwordsMatch) Color(0xFF43A047) else Color(0xFFE53935),
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (passwordsMatch) "Mật khẩu xác nhận hoàn toàn khớp!" else "Mật khẩu xác nhận chưa khớp!",
                  fontSize = 11.sp,
                  color = if (passwordsMatch) Color(0xFF43A047) else Color(0xFFE53935)
                )
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Register Button
            val canRegister = regName.isNotBlank() &&
                regEmail.isNotBlank() &&
                regPassword.length >= 6 &&
                passwordsMatch &&
                (!isOtpSent || regOtpCode.length == 6) &&
                !isRegistering

            Button(
              onClick = {
                focusManager.clearFocus()
                isRegistering = true
                scope.launch {
                  val result = viewModel.authRepo.register(
                    displayNameInput = regName,
                    emailInput = regEmail,
                    passwordInput = regPassword,
                    confirmPasswordInput = regConfirmPassword,
                    otpCodeInput = regOtpCode
                  )
                  isRegistering = false
                  viewModel.showToast(result.second)
                  if (result.first) {
                    viewModel.closeAuthScreen()
                    onBackToGuest()
                  }
                }
              },
              enabled = canRegister,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE91E63),
                disabledContainerColor = Color(0xFFF8BBD0)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_submit_register")
            ) {
              if (isRegistering) {
                CircularProgressIndicator(
                  color = Color.White,
                  modifier = Modifier.size(24.dp),
                  strokeWidth = 2.dp
                )
              } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Đăng Ký Tài Khoản",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Footer badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Favorite,
          contentDescription = null,
          tint = Color(0xFFE91E63),
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "InLove • Lưu giữ từng khoảnh khắc ngọt ngào 💕",
          fontSize = 11.sp,
          color = Color(0xFF757575),
          fontWeight = FontWeight.Medium
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  // ==================== DIALOG QUÊN MẬT KHẨU / PHỤC HỒI ====================
  if (showForgotPasswordDialog) {
    ForgotPasswordDialog(
      viewModel = viewModel,
      onDismiss = { showForgotPasswordDialog = false }
    )
  }
}

@Composable
private fun PasswordCriteriaBadge(
  label: String,
  isMet: Boolean,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (isMet) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
        contentDescription = null,
        tint = if (isMet) Color(0xFF2E7D32) else Color(0xFFC62828),
        modifier = Modifier.size(10.dp)
      )
      Spacer(modifier = Modifier.width(2.dp))
      Text(
        text = label,
        fontSize = 9.sp,
        fontWeight = if (isMet) FontWeight.Bold else FontWeight.Normal,
        color = if (isMet) Color(0xFF2E7D32) else Color(0xFFC62828)
      )
    }
  }
}

@Composable
fun ForgotPasswordDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val scope = rememberCoroutineScope()
  var recoveryMethod by remember { mutableIntStateOf(0) } // 0: OTP 6 số, 1: Câu hỏi bảo mật

  var emailInput by remember { mutableStateOf("") }
  var otpCodeInput by remember { mutableStateOf("") }
  var otpTimerSeconds by remember { mutableIntStateOf(0) }
  var isSendingOtp by remember { mutableStateOf(false) }

  var securityAnswerInput by remember { mutableStateOf("") }
  var newPasswordInput by remember { mutableStateOf("") }
  var confirmNewPasswordInput by remember { mutableStateOf("") }
  var newPasswordVisible by remember { mutableStateOf(false) }
  var isSubmitting by remember { mutableStateOf(false) }

  // OTP Countdown
  LaunchedEffect(otpTimerSeconds) {
    if (otpTimerSeconds > 0) {
      delay(1.seconds)
      otpTimerSeconds -= 1
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(52.dp)
            .background(Color(0xFFFCE4EC), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = Color(0xFFE91E63),
            modifier = Modifier.size(28.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "Khôi Phục Mật Khẩu",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFF212121)
        )
        Text(
          text = "Chọn phương thức xác thực an toàn để đặt lại mật khẩu",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF757575),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(bottom = 14.dp)
        )

        // Segmented selector
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(4.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (recoveryMethod == 0) Color.White else Color.Transparent)
              .clickable { recoveryMethod = 0 }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Mã OTP 6 số",
              fontSize = 13.sp,
              fontWeight = if (recoveryMethod == 0) FontWeight.Bold else FontWeight.Normal,
              color = if (recoveryMethod == 0) Color(0xFFE91E63) else Color(0xFF616161)
            )
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (recoveryMethod == 1) Color.White else Color.Transparent)
              .clickable { recoveryMethod = 1 }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Câu hỏi bảo mật",
              fontSize = 13.sp,
              fontWeight = if (recoveryMethod == 1) FontWeight.Bold else FontWeight.Normal,
              color = if (recoveryMethod == 1) Color(0xFFE91E63) else Color(0xFF616161)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Email Input
        OutlinedTextField(
          value = emailInput,
          onValueChange = { emailInput = it },
          label = { Text("Email tài khoản") },
          placeholder = { Text("Nhập email đã đăng ký") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors = authTextFieldColors(),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (recoveryMethod == 0) {
          // OTP Section
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = otpCodeInput,
              onValueChange = { if (it.length <= 6) otpCodeInput = it },
              label = { Text("Mã OTP (6 số)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              shape = RoundedCornerShape(12.dp),
              colors = authTextFieldColors(),
              modifier = Modifier.weight(1f)
            )

            Button(
              onClick = {
                if (!AuthSecurityManager.isValidEmail(emailInput)) {
                  viewModel.showToast("Vui lòng nhập email hợp lệ!")
                  return@Button
                }
                isSendingOtp = true
                scope.launch {
                  val res = viewModel.authRepo.requestPasswordResetOtp(emailInput)
                  isSendingOtp = false
                  viewModel.showToast(res.second)
                  if (res.first) {
                    otpTimerSeconds = 60
                  }
                }
              },
              enabled = !isSendingOtp && otpTimerSeconds == 0 && emailInput.isNotBlank(),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
              if (isSendingOtp) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
              } else if (otpTimerSeconds > 0) {
                Text("${otpTimerSeconds}s", fontSize = 12.sp)
              } else {
                Text("Gửi mã", fontSize = 12.sp)
              }
            }
          }
        } else {
          // Security Answer Section
          OutlinedTextField(
            value = securityAnswerInput,
            onValueChange = { securityAnswerInput = it },
            label = { Text("Câu trả lời bảo mật") },
            placeholder = { Text("Nhập câu trả lời đã cài khi đăng ký") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = authTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // New Password
        OutlinedTextField(
          value = newPasswordInput,
          onValueChange = { newPasswordInput = it },
          label = { Text("Mật khẩu mới") },
          placeholder = { Text("Tối thiểu 8 ký tự, đủ độ mạnh") },
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
          colors = authTextFieldColors(),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm New Password
        OutlinedTextField(
          value = confirmNewPasswordInput,
          onValueChange = { confirmNewPasswordInput = it },
          label = { Text("Xác nhận mật khẩu mới") },
          visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors = authTextFieldColors(),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Buttons
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
                val res = if (recoveryMethod == 0) {
                  viewModel.authRepo.resetPasswordWithOtp(
                    emailInput = emailInput,
                    enteredOtp = otpCodeInput,
                    newPasswordInput = newPasswordInput,
                    confirmPasswordInput = confirmNewPasswordInput
                  )
                } else {
                  viewModel.authRepo.resetPasswordWithSecurityAnswer(
                    emailInput = emailInput,
                    securityAnswerInput = securityAnswerInput,
                    newPasswordInput = newPasswordInput,
                    confirmPasswordInput = confirmNewPasswordInput
                  )
                }
                isSubmitting = false
                viewModel.showToast(res.second)
                if (res.first) {
                  onDismiss()
                }
              }
            },
            enabled = !isSubmitting && emailInput.isNotBlank() && newPasswordInput.length >= 8,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
            modifier = Modifier.weight(1.5f)
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
            } else {
              Text("Cập Nhật", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}
