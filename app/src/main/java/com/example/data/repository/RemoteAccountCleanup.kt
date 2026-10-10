package com.example.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

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

private const val DELETION_FUNCTIONS_REGION = "asia-southeast1"
private const val NEED_ONLINE_LOGIN = "Cần kết nối mạng và đăng nhập lại để xoá dữ liệu đám mây trước khi xoá tài khoản."

object NoopRemoteAccountCleanup : RemoteAccountCleanup {
  override suspend fun deleteCloudData(uid: String, coupleCode: String) = Unit
  override suspend fun deleteAuthIdentity() = Unit
}

/**
 * Xoá phía máy chủ: gửi yêu cầu cho callable `requestAccountDeletion`; Cloud Functions xoá dữ liệu, tệp và
 * danh tính Auth rồi tự thử lại nếu lỗi giữa chừng. Máy chủ chỉ nhận khi đăng nhập trong vài phút gần đây.
 * Máy chủ chặn bằng App Check + auth_time nên `coupleCode` không còn cần ở client.
 */
class FirebaseRemoteAccountCleanup : RemoteAccountCleanup {
  override suspend fun deleteCloudData(uid: String, coupleCode: String) {
    if (FirebaseAuth.getInstance().currentUser?.uid != uid) throw IllegalStateException(NEED_ONLINE_LOGIN)
    try {
      FirebaseFunctions.getInstance(DELETION_FUNCTIONS_REGION).getHttpsCallable("requestAccountDeletion").call().await()
    } catch (e: FirebaseFunctionsException) {
      // Máy chủ trả details.reason để client phân biệt "cần đăng nhập lại" với lỗi khác.
      val reason = (e.details as? Map<*, *>)?.get("reason")
      if (e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && reason == "recent-login-required") {
        throw RecentLoginRequiredException()
      }
      throw e
    }
  }

  /** Máy chủ đã nhận việc và sẽ xoá danh tính Auth; client chỉ đăng xuất phiên hiện tại. */
  override suspend fun deleteAuthIdentity() {
    FirebaseAuth.getInstance().signOut()
  }
}
