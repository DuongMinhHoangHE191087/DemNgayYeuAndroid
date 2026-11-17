package com.example.data.media

import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.db.InLoveDao
import com.example.domain.DataResidencyPolicy
import com.example.ui.util.ProfileUtils
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.net.URI

/**
 * Avatar cục bộ (`file:` trong Room) -> JPEG nhỏ -> R2 -> đổi giá trị Room sang URL https.
 * Mọi thứ khoá theo uid đang đăng nhập (không dùng isCurrentUser vì hàng khách và hàng tài khoản có thể cùng là 1).
 */
class AvatarPublisher(
  private val dao: InLoveDao,
  private val uploader: AvatarUploader,
  private val avatarsDir: File,
  private val cacheDir: File,
  private val enabled: () -> Boolean = { DataResidencyPolicy.avatarsOnR2 },
  private val signedInUid: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
  private val downscale: (source: File, target: File) -> Boolean = AvatarSizing::downscaleToJpeg,
) {
  enum class Outcome { PUBLISHED, NOTHING_TO_DO, NOT_CONFIGURED, UNAUTHENTICATED, LOST_RACE }

  suspend fun publishPending(): Outcome {
    if (!enabled()) return Outcome.NOTHING_TO_DO
    val uid = signedInUid() ?: return Outcome.UNAUTHENTICATED
    val old = dao.getOnlineUserByUidSync(uid)?.avatarUrl?.takeIf { it.startsWith("file:") } ?: return Outcome.NOTHING_TO_DO

    val source = runCatching { File(URI(old)).canonicalFile }.getOrNull()
      ?.takeIf { it.parentFile == avatarsDir.canonicalFile }
      ?: return Outcome.NOTHING_TO_DO // ngoài thư mục avatars: không đụng vào hàng lẫn file
    if (!source.exists()) {
      // File đã mất thì xoá tham chiếu, kẻo retry vô hạn.
      dao.replaceOnlineUserAvatar(uid, old, "")
      dao.replaceCoupleProfilePicture(old, "")
      return Outcome.NOTHING_TO_DO
    }

    val tmp = File.createTempFile("avatar_", ".jpg", cacheDir)
    try {
      if (!downscale(source, tmp)) return Outcome.NOTHING_TO_DO
      val url = try {
        uploader.upload(tmp, uid)
      } catch (e: CloudFunctionsException) {
        return when {
          e.code == CloudFunctionsCode.FAILED_PRECONDITION && e.reason == "avatars-not-configured" -> Outcome.NOT_CONFIGURED
          e.code == CloudFunctionsCode.UNAUTHENTICATED -> Outcome.UNAUTHENTICATED
          else -> throw e
        }
      }
      // Đăng xuất/đổi tài khoản trong lúc upload: không ghi URL của uid cũ vào Room.
      if (signedInUid() != uid) return Outcome.UNAUTHENTICATED
      // 0 hàng = người dùng đã đổi ảnh khác trong lúc upload: giữ file, để lần sau xử lý giá trị mới.
      if (dao.replaceOnlineUserAvatar(uid, old, url) == 0) return Outcome.LOST_RACE
      dao.replaceCoupleProfilePicture(old, url) // 0 hàng không phải race: thẻ có thể đang giữ giá trị khác
      // Chỉ xoá file cũ khi đọc lại thấy URL mới đã nằm trong Room.
      if (dao.getOnlineUserByUidSync(uid)?.avatarUrl == url) ProfileUtils.staleLocalAvatar(old, url, avatarsDir)?.delete()
      return Outcome.PUBLISHED
    } finally {
      tmp.delete()
    }
  }
}
