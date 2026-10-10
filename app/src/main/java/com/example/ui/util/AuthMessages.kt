package com.example.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.example.data.auth.SocialAuthException.Reason
import com.example.data.repository.UnlockResult

/** The hosting Activity of a (possibly wrapped) Compose context, or null. */
fun Context.findActivity(): Activity? =
  generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

fun socialErrorText(reason: Reason, english: Boolean): String = when (reason) {
  Reason.CANCELLED -> if (english) "Facebook sign-in was cancelled." else "Bạn đã hủy đăng nhập Facebook."
  Reason.NETWORK -> if (english) "No connection. Please try again." else "Không có kết nối mạng. Vui lòng thử lại."
  Reason.ACCOUNT_EXISTS ->
    if (english) "This email already uses another sign-in method. Sign in with email, then link Facebook in Settings."
    else "Email này đã được dùng với phương thức đăng nhập khác. Hãy đăng nhập bằng email rồi liên kết Facebook trong Cài đặt."
  Reason.NOT_CONFIGURED -> if (english) "Facebook sign-in is not available right now." else "Đăng nhập Facebook chưa khả dụng. Vui lòng thử lại sau."
  Reason.MISMATCH -> if (english) "That Facebook account does not match this account." else "Tài khoản Facebook không khớp với tài khoản đang đăng nhập."
  Reason.NO_SESSION -> if (english) "Your session expired. Please sign in again." else "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  Reason.OTHER -> if (english) "Facebook sign-in failed. Please try again." else "Đăng nhập Facebook thất bại. Vui lòng thử lại."
}

/** Text for a failed unlock attempt; null for Success. [secret] is "PIN" or "password". */
fun unlockFailureText(result: UnlockResult, english: Boolean, secret: String = "PIN"): String? = when (result) {
  UnlockResult.Success -> null
  is UnlockResult.Wrong ->
    if (english) "Wrong $secret. ${result.attemptsBeforeLock} tries left before a temporary lock."
    else "Sai ${if (secret == "PIN") "mã PIN" else "mật khẩu"}. Còn ${result.attemptsBeforeLock} lần thử trước khi bị khóa tạm thời."
  is UnlockResult.Locked ->
    if (english) "Too many wrong tries. Please wait before trying again."
    else "Nhập sai quá nhiều lần. Vui lòng chờ rồi thử lại."
  UnlockResult.NoSession -> if (english) "Your session expired. Please sign in again." else "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  is UnlockResult.Error -> socialErrorText(result.reason, english)
}
