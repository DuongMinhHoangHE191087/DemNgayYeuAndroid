package com.app.plugin.firbase

import android.util.Log
import com.app.plugin.until.LogUtil
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Token FCM chỉ được lấy sau consent (Task 9 fix F1). `getToken()` bỏ qua cờ auto-init
 * (firebase-messaging 24.0.2), nên một lời gọi token lúc khởi tạo là tạo token trước consent.
 */
class FirHelperFcmTokenTest {

    /** `FirHelper.instance` có thể đã được tạo bởi test khác trong cùng JVM: tạo bản mới qua reflection. */
    private fun newFirHelper(): FirHelper =
        FirHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    @Test fun `khoi tao FirHelper khong cham FirebaseMessaging`() {
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }
                .thenReturn(mock(FirebaseMessaging::class.java))

            newFirHelper()

            messaging.verify({ FirebaseMessaging.getInstance() }, never())
        }
    }

    // ═══ fetchFcmTokenAfterConsent: lấy token, log như cũ (đã che), mọi lỗi logE ═══

    /** Gọi `fetchFcmTokenAfterConsent` với `getToken()` trả Task hoàn tất ngay; [ok] = thành công, token "tok". */
    private fun fetchWithTask(ok: Boolean, check: (FirebaseMessaging, MockedStatic<Log>) -> Unit) {
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        val task = mock(Task::class.java) as Task<String>
        `when`(task.isSuccessful).thenReturn(ok)
        if (ok) `when`(task.result).thenReturn("tok") else `when`(task.exception).thenReturn(IllegalStateException("no network"))
        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            (inv.getArgument<OnCompleteListener<String>>(0)).onComplete(task)
            task
        }.`when`(task).addOnCompleteListener(any<OnCompleteListener<String>>())
        `when`(fcm.token).thenReturn(task)
        val helper = newFirHelper()
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
            mockStatic(Log::class.java).use { log ->
                helper.fetchFcmTokenAfterConsent()
                check(fcm, log)
            }
        }
    }

    @Test fun `lay token sau consent goi getToken mot lan va log token da che`() =
        fetchWithTask(ok = true) { fcm, log ->
            verify(fcm, times(1)).token
            log.verify { Log.d(LogUtil.TAG, "FCM token fetched [redacted]") }
        }

    @Test fun `lay token that bai thi logE`() =
        fetchWithTask(ok = false) { _, log ->
            log.verify { Log.e(eq(LogUtil.TAG), contains("FCM registration token failed")) }
        }

    @Test fun `FirebaseMessaging nem loi thi logE, khong nem ra ngoai`() {
        val helper = newFirHelper()
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }
                .thenThrow(IllegalStateException("FirebaseApp not initialized"))
            mockStatic(Log::class.java).use { log ->
                helper.fetchFcmTokenAfterConsent()

                log.verify { Log.e(eq(LogUtil.TAG), contains("FCM token fetch failed")) }
            }
        }
    }
}
