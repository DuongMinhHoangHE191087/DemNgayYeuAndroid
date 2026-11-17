package com.example.platform

import android.app.Activity
import android.content.Context
import android.content.Intent

/** Cầu nối chia sẻ văn bản qua hộp chia sẻ của Android. */
class AndroidShareGateway(private val context: Context) : ShareGateway {
  override fun share(text: String, chooserTitle: String?) {
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, chooserTitle)
    if (context !is Activity) {
      // ponytail: a ContextWrapper around an Activity also gets NEW_TASK, harmless for a share sheet.
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }
}
