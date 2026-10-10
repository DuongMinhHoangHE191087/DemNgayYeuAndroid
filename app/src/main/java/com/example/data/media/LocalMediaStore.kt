package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.formatExtensionOf
import com.example.domain.media.validateMemoryMedia
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Kết quả chuẩn bị một lựa chọn: [media] để tải lên, [coverUri] để hiển thị ngay trên máy, hoặc [error] (tiếng Việt). */
data class MemoryPick(val media: MemoryMediaFile?, val coverUri: String?, val error: String?)

/**
 * Sao chép ảnh/video đã chọn vào filesDir/media: URI từ thư viện có thể mất quyền đọc sau khi app đóng.
 * Mọi hàm ở đây đọc/ghi tệp và chặn luồng; gọi trên Dispatchers.IO.
 */
object LocalMediaStore {

  private const val FRAME_TIME_US = 1_000_000L
  private const val JPEG_QUALITY = 92

  /** Sao chép URI vào bộ nhớ app. Trả về null nếu không đọc được; khi lỗi không để lại tệp dở. */
  fun copyPickedUri(context: Context, uri: Uri): File? {
    val extension = formatExtensionOf(mimeOf(context, uri)) ?: "bin"
    val dest = File(mediaDir(context), "picked_${System.currentTimeMillis()}.$extension")
    return try {
      val copied = context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      }
      if (copied == null) {
        dest.delete()
        null
      } else {
        dest
      }
    } catch (e: Exception) {
      dest.delete()
      null
    }
  }

  /** Chuẩn bị một lựa chọn: sao chép, kiểm tra giới hạn, lấy ảnh bìa. Lỗi thì xoá bản sao. */
  fun prepare(context: Context, uri: Uri): MemoryPick {
    val mime = mimeOf(context, uri)
    val copy = copyPickedUri(context, uri) ?: return MemoryPick(null, null, "Không đọc được tệp đã chọn.")
    val kind = if (mime.startsWith("video/")) MediaKind.VIDEO else MediaKind.IMAGE
    val duration = if (kind == MediaKind.VIDEO) durationSecondsOf(copy) else 0
    validateMemoryMedia(kind, mime, copy.length(), duration)?.let { error ->
      copy.delete()
      return MemoryPick(null, null, error)
    }
    val cover = if (kind == MediaKind.VIDEO) saveFrameAsJpeg(context, copy) else copy
    val media = MemoryMediaFile(copy, kind, mime, copy.length(), duration)
    return MemoryPick(media, cover?.let { Uri.fromFile(it).toString() }, null)
  }

  private fun mimeOf(context: Context, uri: Uri): String =
    context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)

  // ponytail: tệp của lựa chọn bị bỏ dở (không lưu) chưa được dọn; thêm dọn khi khởi động nếu bộ nhớ đầy.
  private fun mediaDir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

  // Làm tròn lên: video 60,2 giây bị coi là 61 giây và bị từ chối, đúng với giới hạn 60 giây của máy chủ.
  private fun durationSecondsOf(file: File): Int {
    val ms = withRetriever(file) { it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } ?: 0L
    return ((ms + 999) / 1000).toInt()
  }

  // Ảnh bìa chỉ để hiển thị trên máy; video mới là thứ được tải lên.
  private fun saveFrameAsJpeg(context: Context, video: File): File? {
    val frame = extractVideoFrame(video) ?: return null
    val dest = File(mediaDir(context), "cover_${System.currentTimeMillis()}.jpg")
    return try {
      val saved = FileOutputStream(dest).use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
      if (saved) {
        dest
      } else {
        dest.delete()
        null
      }
    } catch (e: Exception) {
      dest.delete()
      null
    } finally {
      frame.recycle()
    }
  }

  private fun extractVideoFrame(video: File): Bitmap? = withRetriever(video) { retriever ->
    retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
  }

  // minSdk 24: MediaMetadataRetriever chưa AutoCloseable nên phải release() thủ công.
  private inline fun <T> withRetriever(file: File, block: (MediaMetadataRetriever) -> T): T? {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(file.absolutePath)
      block(retriever)
    } catch (e: RuntimeException) {
      null
    } finally {
      retriever.release()
    }
  }
}
