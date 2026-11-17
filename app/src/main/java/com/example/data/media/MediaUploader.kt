package com.example.data.media

import com.example.domain.media.MemoryMediaFile

/** Đẩy tệp bằng HTTP PUT lên URL ký số, gửi đúng các header máy chủ đã ký. Lỗi HTTP ném IOException. */
fun interface MediaUploader {
  suspend fun upload(uploadUrl: String, headers: Map<String, String>, media: MemoryMediaFile)
}
