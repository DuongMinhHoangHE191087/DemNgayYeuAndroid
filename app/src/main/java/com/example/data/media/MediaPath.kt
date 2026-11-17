package com.example.data.media

import java.io.File
import java.net.URLDecoder

/**
 * Đổi giữa đường dẫn tuyệt đối / `file:` URI và dạng tương đối "media/<tên>" (lưu vào Room),
 * và xoá file mồ côi an toàn. Kotlin thuần + java.io.File để chạy được cả JVM test lẫn Android.
 */
object MediaPath {
  const val DIR = "media"
  const val BACKUP_MANIFEST = "memories_backup.json"

  private const val FILE_PREFIX = "file:"

  private fun isFileUri(s: String): Boolean = s.startsWith(FILE_PREFIX, ignoreCase = true)

  // Scheme = tiền tố >= 2 ký tự + ':' (1 chữ cái là ổ đĩa Windows, không phải scheme).
  private fun hasScheme(s: String): Boolean {
    val i = s.indexOf(':')
    return i >= 2 && s.substring(0, i).all { it.isLetterOrDigit() || it == '+' || it == '.' || it == '-' }
  }

  /** `file:` URI hoặc đường dẫn tuyệt đối "trần" (không scheme). */
  private fun isLocalAbsolute(s: String): Boolean =
    isFileUri(s) || (!hasScheme(s) && File(s).isAbsolute)

  /** `file:` URI / đường dẫn tuyệt đối -> "media/<tên>"; mọi thứ khác (https, content:, rỗng, đã tương đối, ngoài media) giữ nguyên. */
  fun toStored(filesDir: File, raw: String?): String? {
    if (raw == null) return null
    if (!isLocalAbsolute(raw)) return raw
    return try {
      val owned = ownedFile(filesDir, raw) ?: return raw
      val base = File(filesDir, DIR).canonicalPath
      val rel = owned.path.substring(base.length + 1).replace(File.separatorChar, '/')
      "$DIR/$rel"
    } catch (_: Exception) {
      raw
    }
  }

  /** "media/<tên>" -> "file://" + đường dẫn tuyệt đối (khi file thuộc thư mục media); còn lại trả nguyên. */
  fun resolve(filesDir: File, stored: String?): String? {
    if (stored == null) return null
    if (isLocalAbsolute(stored) || hasScheme(stored)) return stored
    if (ownedFile(filesDir, stored) == null) return stored
    return "file://" + File(filesDir, stored).absolutePath
  }

  /** File mà giá trị lưu trỏ tới, chỉ khi canonical path nằm NGHIÊM NGẶT trong canonical filesDir/media. */
  fun ownedFile(filesDir: File, stored: String?): File? {
    if (stored.isNullOrBlank()) return null
    return try {
      val f = when {
        isFileUri(stored) -> File(URLDecoder.decode(stored.substring(FILE_PREFIX.length).replace("+", "%2B"), "UTF-8"))
        hasScheme(stored) -> return null
        File(stored).isAbsolute -> File(stored)
        else -> File(filesDir, stored)
      }
      val base = File(filesDir, DIR).canonicalPath
      val canonical = f.canonicalPath
      if (canonical.startsWith(base + File.separator) && canonical != base) File(canonical) else null
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Xoá các file owned của [candidates] mà không giá trị nào trong [stillReferenced] ánh xạ tới
   * (so sánh theo canonical path). Trả số file đã xoá. Không bao giờ ném lỗi.
   */
  fun deleteUnreferenced(filesDir: File, candidates: List<String?>, stillReferenced: List<String?>): Int {
    return try {
      val kept = stillReferenced.mapNotNull { ownedFile(filesDir, it)?.path }.toSet()
      candidates.mapNotNull { ownedFile(filesDir, it) }
        .distinctBy { it.path }
        .count { it.path !in kept && it.isFile && runCatching { it.delete() }.getOrDefault(false) }
    } catch (_: Exception) {
      0
    }
  }
}
