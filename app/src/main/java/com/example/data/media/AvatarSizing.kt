package com.example.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.example.domain.media.AVATAR_MAX_BYTES
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Thu nhỏ ảnh đại diện về JPEG nhỏ trước khi đẩy lên R2. Hai hàm đầu là toán thuần (có test), hàm cuối chạy trên máy thật. */
object AvatarSizing {
  const val MAX_SIDE = 512

  private val JPEG_QUALITIES = intArrayOf(85, 75, 65, 60)

  /** Giữ tỉ lệ, không phóng to, mỗi cạnh tối thiểu 1 px. Đầu vào không dương trả (1, 1). */
  fun targetSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 1 to 1
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val ratio = maxSide.toDouble() / longest
    return Math.round(width * ratio).toInt().coerceAtLeast(1) to Math.round(height * ratio).toInt().coerceAtLeast(1)
  }

  /** Luỹ thừa 2 lớn nhất s sao cho cạnh dài / s vẫn >= maxSide (decode xong không nhỏ hơn đích). */
  fun inSampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
    if (width <= 0 || height <= 0) return 1
    val longest = maxOf(width, height)
    var s = 1
    while (longest / (s * 2) >= maxSide) s *= 2
    return s
  }

  /**
   * Giải mã [source] (mọi định dạng ảnh chọn từ thư viện), xoay theo EXIF, thu về tối đa [MAX_SIDE] px,
   * dán lên nền trắng (PNG trong suốt không thành đen) rồi nén JPEG vào [target]. False khi không đọc/nén vừa được.
   * Không có unit test: BitmapFactory của Robolectric không chắc giải mã được ảnh thật trên máy host.
   */
  fun downscaleToJpeg(source: File, target: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

    val sample = inSampleSize(bounds.outWidth, bounds.outHeight)
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeFile(source.path, opts) ?: return false

    val oriented = orient(decoded, exifOrientation(source))
    val (w, h) = targetSize(oriented.width, oriented.height)
    val scaled = oriented.scale(w, h)
    val flat = createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(flat).apply {
      drawColor(Color.WHITE)
      drawBitmap(scaled, 0f, 0f, null)
    }
    // createScaledBitmap/createBitmap có thể trả lại chính bitmap vào: chỉ recycle khi là đối tượng khác nhau.
    if (scaled !== oriented) scaled.recycle()
    if (oriented !== decoded) oriented.recycle()
    decoded.recycle()

    try {
      for (quality in JPEG_QUALITIES) {
        val ok = FileOutputStream(target).use { flat.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (ok && target.length() in 1..AVATAR_MAX_BYTES) return true
      }
      return false
    } finally {
      flat.recycle()
    }
  }

  private fun exifOrientation(source: File): Int = try {
    ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
  } catch (e: IOException) {
    ExifInterface.ORIENTATION_NORMAL // không đọc được EXIF = coi như ảnh thẳng
  }

  private fun orient(src: Bitmap, orientation: Int): Bitmap {
    val m = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
      else -> return src
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
  }
}
