package com.app.plugin.iap

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryPurchasesParams
import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 1.6.4 Task 1 (nhận `a059e61`, dạng `private`): store trả lỗi ở một trong hai lượt hỏi của `restorePurchases`
 * (INAPP rồi SUBS) thì giữ nguyên quyền lợi đang có — không coi lỗi là "không sở hữu gì".
 */
class IapHelperRestoreTest {
    /**
     * F2B: cổng sẵn sàng là "đã `startConnection` + không đang kết nối", không còn `client.isReady` (Billing 9 luôn true).
     * `connect` trên mock (startConnection không làm gì) bật cờ đó trong port — thay `started(true)` qua reflection cũ.
     */
    private fun portOn(client: BillingClient): BillingPort = PlayBillingPort(client).also { it.connect { } }

    @Before fun setUp() { Entitlements.clearAll(); useDirectBillingExecutor() }
    @After fun tearDown() { IapHelper.replacePort(null); Entitlements.clearAll() }

    /** Lượt 1 (INAPP) trả `inappCode` + [inapp], lượt 2 (SUBS) trả `subsCode`, danh sách rỗng. */
    private fun clientAnswering(inappCode: Int, subsCode: Int, inapp: List<Purchase> = emptyList()): BillingClient {
        val client = mock(BillingClient::class.java)
        `when`(client.isReady).thenReturn(true)
        `when`(client.connectionState).thenReturn(BillingClient.ConnectionState.CONNECTED)
        var n = 0
        doAnswer { inv ->
            val first = n++ == 0
            inv.getArgument<PurchasesResponseListener>(1).onQueryPurchasesResponse(
                BillingResult.newBuilder().setResponseCode(if (first) inappCode else subsCode).build(), if (first) inapp else emptyList()
            )
            null
        }.`when`(client).queryPurchasesAsync(any(QueryPurchasesParams::class.java), any(PurchasesResponseListener::class.java))
        return client
    }

    @Test fun `luot hoi mua mot lan loi thi giu quyen loi`() {
        Entitlements.grant("premium")
        IapHelper.replacePort(portOn(clientAnswering(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.OK)))
        var got: Set<String>? = null
        IapHelper.restorePurchases { got = it }
        assertEquals("onDone mang quyền lợi hiện có", setOf("premium"), got)
        assertTrue("store lỗi (inapp) không được tước quyền lợi", Entitlements.isActive("premium"))
    }

    @Test fun `luot hoi thue bao loi thi giu quyen loi`() {
        Entitlements.grant("premium")
        IapHelper.replacePort(portOn(clientAnswering(BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)))
        var got: Set<String>? = null
        IapHelper.restorePurchases { got = it }
        assertEquals("onDone mang quyền lợi hiện có", setOf("premium"), got)
        assertTrue("store lỗi (subs) không được tước quyền lợi", Entitlements.isActive("premium"))
    }

    @Test fun `ca hai luot OK ma store khong con san pham thi thu hoi (dac ta giu)`() {
        Entitlements.grant("premium")
        IapHelper.replacePort(portOn(clientAnswering(BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.OK)))
        IapHelper.restorePurchases()
        assertFalse("cả hai lượt OK, store rỗng: thu hồi", Entitlements.isActive("premium"))
    }

    /** Task R #12 (info gap 1.8.0, 1.9.0 Task 11): khôi phục thành công (cả hai lượt OK) ghi `IapHelper restore OK owned=<n>`. */
    @Test fun `F7 khoi phuc thanh cong ghi dong restore OK owned`() {
        val owned = mock(Purchase::class.java)
        `when`(owned.products).thenReturn(listOf("premium"))
        `when`(owned.purchaseToken).thenReturn("tokP")
        `when`(owned.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        `when`(owned.isAcknowledged).thenReturn(true)
        IapHelper.replacePort(portOn(clientAnswering(BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.OK, listOf(owned))))
        LogUtil.setFlagLog(3)
        try {
            mockStatic(Log::class.java).use { log ->
                IapHelper.restorePurchases()
                log.verify({ Log.d(LogUtil.TAG, "IapHelper restore OK owned=1") }, times(1))
            }
        } finally {
            LogUtil.setFlagLog(1)
            PurchaseLedger.resetForTest()
        }
        assertTrue(Entitlements.isActive("premium"))
    }
}
