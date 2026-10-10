package com.example.domain.media

import java.io.File
import java.util.Locale

// Phải khớp firebase/functions/src/config.js. Máy chủ vẫn kiểm tra lại mọi giới hạn này.
const val MEMORY_PUBLIC_ID_PREFIX = "inlove_mem_"
private const val IMAGE_MAX_BYTES = 10L * 1024 * 1024
private const val VIDEO_MAX_BYTES = 50L * 1024 * 1024
private const val VIDEO_MAX_SECONDS = 60
// MIME -> định dạng Cloudinary (cũng là đuôi tệp trên máy), khớp mimeFormats trong config.js.
private val IMAGE_FORMATS = mapOf(
  "image/jpeg" to "jpg", "image/jpg" to "jpg", "image/png" to "png",
  "image/webp" to "webp", "image/heic" to "heic", "image/heif" to "heic",
)
private val VIDEO_FORMATS = mapOf("video/mp4" to "mp4", "video/quicktime" to "mov", "video/webm" to "webm")
// Danh sách định dạng trong thông báo lỗi lấy từ bảng trên, để thông báo không lệch khỏi quy tắc thật.
private fun formatList(formats: Map<String, String>): String =
  formats.values.distinct().joinToString(", ") { it.uppercase(Locale.ROOT) }

enum class MediaKind { IMAGE, VIDEO }

data class MemoryMediaFile(
  val file: File,
  val kind: MediaKind,
  val mimeType: String,
  val sizeBytes: Long,
  val durationSeconds: Int = 0,
)

/** Tải lên qua đường ký số: máy chủ cấp chỗ, app gửi thẳng lên Cloudinary, rồi máy chủ xác nhận. */
interface MemoryMediaRepository {
  /** Trả về publicId đã được máy chủ xác nhận. */
  suspend fun upload(relationshipId: String, memoryId: String, media: MemoryMediaFile): String

  /** URL hiển thị/phát có hạn cho kỷ niệm của cặp đôi này. */
  suspend fun deliveryUrl(relationshipId: String, memoryId: String): String
}

/** Thông báo lỗi (tiếng Việt), hoặc null khi tệp được phép tải lên. */
fun validateMemoryMedia(kind: MediaKind, mimeType: String, sizeBytes: Long, durationSeconds: Int): String? {
  val mime = mimeType.lowercase(Locale.ROOT)
  return when (kind) {
    MediaKind.IMAGE -> when {
      mime !in IMAGE_FORMATS -> "Định dạng ảnh chưa được hỗ trợ (${formatList(IMAGE_FORMATS)})."
      sizeBytes <= 0 || sizeBytes > IMAGE_MAX_BYTES -> "Ảnh vượt quá ${IMAGE_MAX_BYTES / (1024 * 1024)} MB (${formatMediaSize(sizeBytes)}). Vui lòng chọn ảnh nhẹ hơn."
      else -> null
    }
    MediaKind.VIDEO -> when {
      mime !in VIDEO_FORMATS -> "Định dạng video chưa được hỗ trợ (${formatList(VIDEO_FORMATS)})."
      sizeBytes <= 0 || sizeBytes > VIDEO_MAX_BYTES -> "Video vượt quá ${VIDEO_MAX_BYTES / (1024 * 1024)} MB (${formatMediaSize(sizeBytes)})."
      durationSeconds !in 1..VIDEO_MAX_SECONDS -> "Video phải dài từ 1 đến $VIDEO_MAX_SECONDS giây (hiện tại: $durationSeconds giây)."
      else -> null
    }
  }
}

/** Đuôi tệp trên máy cho một MIME được hỗ trợ, hoặc null. */
fun formatExtensionOf(mimeType: String): String? {
  val mime = mimeType.lowercase(Locale.ROOT)
  return IMAGE_FORMATS[mime] ?: VIDEO_FORMATS[mime]
}

fun formatMediaSize(bytes: Long): String =
  if (bytes >= 1024L * 1024) String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
  else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
