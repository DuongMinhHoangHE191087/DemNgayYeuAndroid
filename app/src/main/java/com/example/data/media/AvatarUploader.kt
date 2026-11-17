package com.example.data.media

import com.example.data.cloud.CloudFunctionsClient
import com.example.domain.media.AVATAR_PUBLIC_ID_PREFIX
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException

/**
 * Ảnh đại diện JPEG: máy chủ cấp URL ký số, app PUT thẳng lên R2 (bucket công khai) rồi dùng publicUrl làm avatarUrl.
 * Chỉ chạy khi avatarsOnR2 bật. Không có bước confirm và không đọc lại nội dung; rủi ro này đã được chấp nhận trong kế hoạch.
 */
class AvatarUploader(
  private val functions: CloudFunctionsClient,
  private val uploader: MediaUploader,
) {

  /** Trả về publicUrl do máy chủ cấp (đã kiểm tra), dùng nguyên văn làm avatarUrl. */
  suspend fun upload(jpeg: File, uid: String): String {
    val size = jpeg.length()
    val reply = functions.call("signAvatarUpload", mapOf("contentType" to "image/jpeg", "contentLength" to size))
    // Mọi kiểm tra xảy ra trước khi PUT: phản hồi xấu thì không có PUT nào.
    val publicId = (reply["publicId"] as? String)?.takeIf { PUBLIC_ID_REGEX.matches(it) } ?: throw IOException(BAD_RESPONSE)
    // publicId phải là của chính uid đang đăng nhập, kẻo ghi đè avatar của người khác.
    if (publicId != AVATAR_PUBLIC_ID_PREFIX + uid) throw IOException(BAD_RESPONSE)
    val signed = parseSignedUpload(reply, publicId)
    // publicUrl nằm trên host công khai (không phải r2.cloudflarestorage.com) nên chỉ kiểm https và đuôi path.
    val publicUrl = reply["publicUrl"] as? String
    val parsed = publicUrl?.toHttpUrlOrNull()
    if (publicUrl == null || parsed == null || !parsed.isHttps || !parsed.encodedPath.endsWith("/$publicId")) {
      throw IOException(BAD_RESPONSE)
    }
    uploader.upload(
      signed.uploadUrl,
      signed.headers,
      MemoryMediaFile(path = jpeg.path, kind = MediaKind.IMAGE, mimeType = "image/jpeg", sizeBytes = size),
    )
    return publicUrl
  }

  private companion object {
    val PUBLIC_ID_REGEX = Regex("^${Regex.escape(AVATAR_PUBLIC_ID_PREFIX)}[A-Za-z0-9_-]{1,128}$")
    const val BAD_RESPONSE = "Phản hồi không hợp lệ từ máy chủ"
  }
}
