package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState

/**
 * Ảnh hiển thị của kỷ niệm: ưu tiên bản trên máy; máy của người kia không có bản đó nên xin URL có hạn từ đám mây.
 * [resolve] trả về null khi không lấy được; khi đó không hiển thị gì thay vì lỗi.
 */
@Composable
fun rememberMemoryUri(
  memoryId: String,
  localUri: String?,
  cloudPublicId: String?,
  resolve: suspend () -> String?,
): String? {
  val remote by produceState<String?>(initialValue = null, memoryId, localUri, cloudPublicId) {
    value = if (localUri.isNullOrBlank() && !cloudPublicId.isNullOrBlank()) resolve() else null
  }
  return if (localUri.isNullOrBlank()) remote else localUri
}
