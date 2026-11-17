package com.example.data.repository

import com.example.data.cloud.AndroidCloudFunctionsClient
import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException

/** Máy chủ đòi đăng nhập lại gần đây trước khi xoá tài khoản; người dùng đăng nhập lại rồi thử xoá lần nữa. */
class RecentLoginRequiredException : Exception()

/**
 * Dọn dữ liệu đám mây khi xoá tài khoản. Là interface để luồng xoá trong AuthRepository test được
 * mà không cần Firebase (cùng cách với FacebookAuthGateway).
 */
interface RemoteAccountCleanup {
  /** Phải chạy khi còn đăng nhập. Bản Firebase gửi yêu cầu cho máy chủ, máy chủ xoá và tự thử lại. */
  suspend fun deleteCloudData(uid: String, coupleCode: String)

  /** Kết thúc phiên Firebase Auth hiện tại (máy chủ xoá danh tính ở phía nó). */
  suspend fun deleteAuthIdentity()
}

private const val NEED_ONLINE_LOGIN = "Cần kết nối mạng và đăng nhập lại để xoá dữ liệu đám mây trước khi xoá tài khoản."
private const val CLOUD_REQUEST_FAILED =
  "Chưa gửi được yêu cầu xoá lên máy chủ (mạng chậm hoặc mất kết nối). Tài khoản vẫn còn nguyên, vui lòng thử lại."

object NoopRemoteAccountCleanup : RemoteAccountCleanup {
  override suspend fun deleteCloudData(uid: String, coupleCode: String) = Unit
  override suspend fun deleteAuthIdentity() = Unit
}

/**
 * Xoá phía máy chủ: gửi yêu cầu cho callable `requestAccountDeletion` (qua CloudFunctionsClient: timeout 20s + ánh xạ lỗi);
 * Cloud Functions xoá dữ liệu, tệp và danh tính Auth rồi tự thử lại nếu lỗi giữa chừng. Máy chủ chỉ nhận khi đăng nhập
 * trong vài phút gần đây. Máy chủ chặn bằng App Check + auth_time nên `coupleCode` không còn cần ở client.
 */
class FirebaseRemoteAccountCleanup(
  private val functions: CloudFunctionsClient = AndroidCloudFunctionsClient(),
  private val currentUid: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
) : RemoteAccountCleanup {
  override suspend fun deleteCloudData(uid: String, coupleCode: String) {
    if (currentUid() != uid) throw IllegalStateException(NEED_ONLINE_LOGIN)
    try {
      functions.call("requestAccountDeletion")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Máy chủ trả details.reason để client phân biệt "cần đăng nhập lại" với lỗi khác.
      val recentLogin = e is CloudFunctionsException &&
        e.code == CloudFunctionsCode.FAILED_PRECONDITION && e.reason == "recent-login-required"
      if (recentLogin) throw RecentLoginRequiredException()
      throw IllegalStateException(CLOUD_REQUEST_FAILED, e)
    }
  }

  /** Máy chủ đã nhận việc và sẽ xoá danh tính Auth; client chỉ đăng xuất phiên hiện tại. */
  override suspend fun deleteAuthIdentity() {
    FirebaseAuth.getInstance().signOut()
  }
}
