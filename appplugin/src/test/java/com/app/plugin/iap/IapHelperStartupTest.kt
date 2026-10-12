package com.app.plugin.iap

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.app.plugin.SdkKillSwitch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1.6.4 Task 1 (#11 Samsung 1.6.3, `FreecessController`): `init` không dựng `BillingClient` và không
 * `startConnection` trên luồng gọi (main lúc `Application.onCreate`) — cả hai đi qua binder. Chỉ lên lịch
 * một việc trên luồng nền "appplugin-billing".
 */
class IapHelperStartupTest {
    private val queued = mutableListOf<Runnable>()

    /** Seam `private` (không vào dump): có thì thay bằng executor giữ việc lại; mã cũ không có thì `init` chạy đồng bộ → đỏ ở verify. */
    private fun captureExecutor() = IapHelper::class.java.declaredFields.firstOrNull { it.name == "billingExecutor" }
        ?.apply { isAccessible = true }?.set(null, Executor { queued += it })

    private fun contextMock(): Context = mock(Context::class.java).also { `when`(it.applicationContext).thenReturn(it) }

    private fun builderReturning(client: BillingClient): BillingClient.Builder =
        mock(BillingClient.Builder::class.java, RETURNS_SELF).also { `when`(it.build()).thenReturn(client) }

    @Before fun setUp() { SdkKillSwitch.current = SdkKillSwitch.NONE }

    /** Reset cả `connectScheduled`: bỏ bước này thì test kill switch chạy sau trong cùng JVM thấy "đã lên lịch" và đỏ oan. */
    @After fun tearDown() {
        useDirectBillingExecutor() // không để executor gom việc của test này cho test sau
        IapHelper.replacePort(null) // cờ "đã startConnection" nằm trong port: port mới mỗi lần init
        IapHelper::class.java.declaredFields.firstOrNull { it.name == "connectScheduled" }?.apply { isAccessible = true }
            ?.let { (it.get(null) as AtomicBoolean).set(false) }
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    @Test fun `init chi len lich, khong dung BillingClient tren luong goi, chay lich thi ket noi dung mot lan`() {
        val client = mock(BillingClient::class.java)
        val builder = builderReturning(client)
        captureExecutor()
        mockStatic(BillingClient::class.java).use { billing ->
            billing.`when`<BillingClient.Builder> { BillingClient.newBuilder(any()) }.thenReturn(builder)

            assertTrue("init báo đã lên lịch", IapHelper.init(contextMock()))
            billing.verify({ BillingClient.newBuilder(any()) }, never())
            verify(client, never()).startConnection(any(BillingClientStateListener::class.java))
            assertEquals("một việc được lên lịch", 1, queued.size)

            queued.forEach { it.run() }
            billing.verify({ BillingClient.newBuilder(any()) }, times(1))
            verify(client, times(1)).startConnection(any(BillingClientStateListener::class.java))
        }
    }

    @Test fun `init goi lai truoc khi lich chay khong len lich lan hai`() {
        val client = mock(BillingClient::class.java)
        val builder = builderReturning(client)
        captureExecutor()
        mockStatic(BillingClient::class.java).use { billing ->
            billing.`when`<BillingClient.Builder> { BillingClient.newBuilder(any()) }.thenReturn(builder)

            assertTrue("init lần một báo đã lên lịch", IapHelper.init(contextMock()))
            assertTrue("init lần hai (lịch chưa chạy) vẫn báo true", IapHelper.init(contextMock()))
            billing.verify({ BillingClient.newBuilder(any()) }, never())
            assertEquals("lần gọi hai không lên lịch thêm", 1, queued.size)
        }
    }

    @Test fun `S3 kill switch toi sau khi da len lich - init lan hai van true, khong len lich them`() {
        val client = mock(BillingClient::class.java)
        val builder = builderReturning(client)
        captureExecutor()
        mockStatic(BillingClient::class.java).use { billing ->
            billing.`when`<BillingClient.Builder> { BillingClient.newBuilder(any()) }.thenReturn(builder)

            assertTrue("init lần một báo đã lên lịch", IapHelper.init(contextMock()))
            SdkKillSwitch.current = SdkKillSwitch(iap = true)
            assertTrue("kill switch tới sau lịch: init lần hai vẫn true (billing đang kết nối)", IapHelper.init(contextMock()))
            assertEquals("không lên lịch thêm", 1, queued.size)
            billing.verify({ BillingClient.newBuilder(any()) }, never())
        }
    }
}
