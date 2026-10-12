package com.app.plugin.iap

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ConnectionState
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.app.plugin.SdkKillSwitch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1.6.4 F2B (Samsung A25 gate run 3, 2026-09-30): Billing 9 với `enableAutoServiceReconnection()` cho
 * `BillingClient.isReady()` = true ở MỌI trạng thái (bytecode `billing:9.0.0`: `if (zzG) return true`).
 * Cổng `isReady` của SDK vì thế không chặn gì: `AppPluginBase.onStart` gọi `restorePurchases` giữa lúc
 * kết nối đầu còn chạy, thư viện thử "kết nối lại" chồng lên (3 lần, ~3 s) rồi trả lỗi. Sẵn sàng thật =
 * đã `startConnection` và không đang kết nối; DISCONNECTED sau đó vẫn sẵn sàng (auto-reconnect lười).
 */
class IapHelperReadyTest {
    private val queued = mutableListOf<Runnable>()

    private fun field(name: String) =
        IapHelper::class.java.declaredFields.firstOrNull { it.name == name }?.apply { isAccessible = true }

    /** Client giống Billing 9 bật auto-reconnect: `isReady` luôn true, trạng thái thật do test đặt. */
    private fun billing9Client(state: Int): BillingClient = mock(BillingClient::class.java).also {
        `when`(it.isReady).thenReturn(true)
        `when`(it.connectionState).thenReturn(state)
    }

    /** Chạy đường thật `init` → luồng billing → `startConnection` (trên mock, không làm gì). */
    private fun startedWith(client: BillingClient) {
        field("billingExecutor")?.set(null, Executor { queued += it })
        val builder = mock(BillingClient.Builder::class.java, RETURNS_SELF).also { `when`(it.build()).thenReturn(client) }
        val context = mock(Context::class.java).also { `when`(it.applicationContext).thenReturn(it) }
        mockStatic(BillingClient::class.java).use { billing ->
            billing.`when`<BillingClient.Builder> { BillingClient.newBuilder(any()) }.thenReturn(builder)
            assertTrue(IapHelper.init(context))
            while (queued.isNotEmpty()) queued.removeAt(0).run() // 1.8.0 T5: việc chạy trên luồng billing có thể hẹn thêm việc
        }
    }

    @Before fun setUp() { SdkKillSwitch.current = SdkKillSwitch.NONE; Entitlements.clearAll() }

    @After fun tearDown() {
        useDirectBillingExecutor() // không để executor gom việc của test này cho test sau
        IapHelper.replacePort(null) // cờ "đã startConnection" nằm trong port: port mới mỗi test
        (field("connectScheduled")?.get(null) as AtomicBoolean?)?.set(false)
        @Suppress("UNCHECKED_CAST")
        (field("productCache")?.get(null) as MutableMap<String, IapProduct>?)?.clear()
        Entitlements.clearAll()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    @Test fun `dang ket noi lan dau thi chua san sang du BillingClient isReady true`() {
        startedWith(billing9Client(ConnectionState.CONNECTING))
        assertFalse("CONNECTING: isReady() của SDK phải false (Billing 9 isReady luôn true)", IapHelper.isReady())
    }

    @Test fun `restore trong luc dang ket noi khong hoi store va tra quyen loi da luu`() {
        val client = billing9Client(ConnectionState.CONNECTING)
        startedWith(client)
        Entitlements.grant("premium")
        var got: Set<String>? = null

        IapHelper.restorePurchases { got = it }

        verify(client, never()).queryPurchasesAsync(any(QueryPurchasesParams::class.java), any(PurchasesResponseListener::class.java))
        assertEquals("restore lúc CONNECTING trả quyền lợi đã lưu", setOf("premium"), got)
    }

    @Test fun `mua trong luc dang ket noi bi tu choi, khong goi store`() {
        val client = billing9Client(ConnectionState.CONNECTING)
        startedWith(client)
        @Suppress("UNCHECKED_CAST")
        (field("productCache")!!.get(null) as MutableMap<String, IapProduct>)["coin"] =
            IapProduct("coin", IapProductType.INAPP, "1 $", 1_000_000L, "USD")
        var result: IapResult? = null

        val launched = IapHelper.purchase(mock(Activity::class.java), "coin") { result = it }

        assertFalse("purchase lúc CONNECTING trả false", launched)
        assertTrue("callback nhận Failed", result is IapResult.Failed)
        verify(client, never()).queryProductDetailsAsync(
            any(QueryProductDetailsParams::class.java), any(ProductDetailsResponseListener::class.java)
        )
    }

    /** Khe của T1: `BillingClient` đã gán trên luồng billing nhưng `startConnection` chưa chạy. */
    @Test fun `client da dung nhung chua startConnection thi chua san sang`() {
        IapHelper.replacePort(PlayBillingPort(billing9Client(ConnectionState.DISCONNECTED)))
        assertFalse("chưa startConnection: chưa sẵn sàng", IapHelper.isReady())
    }

    /** Callback CONNECTED có thể tới (luồng thư viện) trước dòng đặt cờ sau `startConnection`: lượt khôi phục đầu vẫn chạy. */
    @Test fun `ket noi xong truoc khi co duoc dat van khoi phuc ngay`() {
        val client = billing9Client(ConnectionState.CONNECTED)
        doAnswer { inv ->
            inv.getArgument<BillingClientStateListener>(0)
                .onBillingSetupFinished(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build())
            null
        }.`when`(client).startConnection(any(BillingClientStateListener::class.java))
        startedWith(client)
        verify(client).queryPurchasesAsync(any(QueryPurchasesParams::class.java), any(PurchasesResponseListener::class.java))
    }

    /** R-F2B RA: cờ chỉ bật SAU khi `startConnection` trả về; bật trước thì khe T1 mở lại (thư viện tự khởi kết nối, mất CONNECTED). */
    @Test fun `trong luc startConnection dang chay thi chua san sang`() {
        val client = billing9Client(ConnectionState.DISCONNECTED)
        var readyDuring: Boolean? = null
        doAnswer { readyDuring = IapHelper.isReady(); null }
            .`when`(client).startConnection(any(BillingClientStateListener::class.java))
        startedWith(client)
        assertEquals("đang trong startConnection (DISCONNECTED): chưa sẵn sàng", false, readyDuring)
        assertTrue("startConnection xong: sẵn sàng", IapHelper.isReady())
    }

    /** Giữ đường tự kết nối lại của Billing 9: sau khi đã start, DISCONNECTED vẫn cho gọi (lời gọi sẽ tự reconnect). */
    @Test fun `da start roi thi DISCONNECTED va CONNECTED deu san sang`() {
        val client = billing9Client(ConnectionState.DISCONNECTED)
        startedWith(client)
        assertTrue("DISCONNECTED sau start: sẵn sàng (auto-reconnect)", IapHelper.isReady())
        `when`(client.connectionState).thenReturn(ConnectionState.CONNECTED)
        assertTrue("CONNECTED: sẵn sàng", IapHelper.isReady())
    }
}
