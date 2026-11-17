package com.example.data.media.fakes

import com.example.data.media.MediaUploader
import com.example.domain.media.MemoryMediaFile

/** Uploader giả cho test: ghi lại lời gọi, ném `failure` nếu được đặt. */
class FakeMediaUploader : MediaUploader {
  data class Call(val uploadUrl: String, val headers: Map<String, String>, val media: MemoryMediaFile)

  val calls = mutableListOf<Call>()
  var failure: Throwable? = null

  /** Gọi mỗi lần upload, trước khi ném `failure`; test dùng để chèn mốc thứ tự. */
  var onUpload: (() -> Unit)? = null

  override suspend fun upload(uploadUrl: String, headers: Map<String, String>, media: MemoryMediaFile) {
    calls += Call(uploadUrl, headers, media)
    onUpload?.invoke()
    failure?.let { throw it }
  }
}
