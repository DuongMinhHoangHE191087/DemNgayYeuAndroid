package com.app.plugin.iap

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.CompletableDeferred

/**
 * Lớp bọc mỏng quanh Play Integrity API: xin một token chứng thực để server của app kiểm tra
 * (qua Play Integrity Server API) trước khi cấp quyền lợi cho một giao dịch mua.
 *
 * ═══ KIỂU REQUEST: CLASSIC ═══
 *
 * Dùng request classic (`IntegrityTokenRequest` + nonce), không dùng standard. Standard hợp cho
 * lời gọi thường xuyên, cần `prepareIntegrityToken` hâm nóng trước và ràng buộc bằng
 * `requestHash`. Ở đây là lời gọi hiếm, giá trị cao, mỗi lần một giao dịch: nonce gắn với
 * `purchaseToken` để token cũ không dùng lại được cho giao dịch mới. Bản fork ở Speaker Clean
 * cũng gọi classic, dù comment ghi "Standard".
 *
 * ═══ FAIL-OPEN ═══
 *
 * Lỗi hay hết giờ thì trả `null` chứ không ném: người dùng đã mua hợp lệ không nên bị phạt vì
 * mạng chập chờn. Việc từ chối gian lận là của server: không có token nghĩa là "chưa kiểm
 * chứng", server hạ mức tin cậy và KHÔNG tự cấp quyền lợi.
 *
 * ═══ SẼ XOÁ Ở 3.0 ═══
 *
 * Mã chết (0 nơi gọi): xoá ở 3.0 (D12-220). Còn giữ vì có trong dump ABI (xoá ở 2.x = phá ABI); không gắn
 * `@Deprecated` để dump không đổi. Nửa client Play Integrity thật = 2.3.0 sau O3 (SEC-B).
 */
internal object PlayIntegrityGuard {

    private const val TAG = "PlayIntegrityGuard"

    /**
     * @param nonce chuỗi base64 web-safe, 16–500 ký tự, nên dẫn xuất từ `purchaseToken`.
     * @param cloudProjectNumber số hiệu Google Cloud project (Firebase Console → Project Settings
     *   → General → Project number).
     * @return token, hoặc `null` khi có bất kỳ lỗi nào.
     */
    public suspend fun requestToken(context: Context, nonce: String, cloudProjectNumber: Long): String? {
        return try {
            val manager = IntegrityManagerFactory.create(context.applicationContext)
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
            val deferred = CompletableDeferred<String?>()
            manager.requestIntegrityToken(request)
                .addOnSuccessListener { response -> deferred.complete(response.token()) }
                .addOnFailureListener { err ->
                    Log.w(TAG, "requestIntegrityToken failed", err)
                    deferred.complete(null)
                }
            deferred.await()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.w(TAG, "requestIntegrityToken threw", t)
            null
        }
    }
}
