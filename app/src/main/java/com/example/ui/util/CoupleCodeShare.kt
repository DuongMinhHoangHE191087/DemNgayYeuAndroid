package com.example.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

// copyCoupleCode callers show their own toast; shareCoupleCode toasts only when no app can handle the share.

internal fun copyCoupleCode(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val clip = ClipData.newPlainText("Couple Code", text)
  @Suppress("UsePropertyAccessSyntax")
  clipboard.setPrimaryClip(clip)
}

internal fun shareCoupleCode(context: Context, code: String, isEnglish: Boolean) {
  val shareLink = ProfileUtils.createShareLink(code)
  val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(
      Intent.EXTRA_TEXT,
      if (isEnglish) "Connect 1-1 with me on InLove! Open link or enter code:\n$shareLink\nCouple code: $code ❤️"
      else "Cùng kết nối Set Love 1-1 với mình trên InLove nhé! Bấm vào link hoặc nhập mã:\n$shareLink\nMã ghép đôi: $code ❤️"
    )
  }
  runCatching {
    context.startActivity(
      Intent.createChooser(shareIntent, if (isEnglish) "Share couple code via" else "Chia sẻ mã/link ghép đôi qua")
    )
  }.onFailure {
    Toast.makeText(context, if (isEnglish) "No app can share this" else "Không có ứng dụng để chia sẻ", Toast.LENGTH_SHORT).show()
  }
}
